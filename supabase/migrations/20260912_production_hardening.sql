-- Clarys - endurecimiento previo a producción.
-- Revisar en un entorno de prueba antes de ejecutar en el proyecto principal.

begin;

-- El catálogo anónimo solo puede consultar información comercial pública.
revoke select on table public.products from anon;
grant select (
    id,
    workshop_id,
    name,
    description,
    category,
    sale_price,
    stock,
    sizes,
    colors,
    sku,
    image_url,
    active
) on table public.products to anon;

revoke select on table public.workshops from anon;
grant select (id, name, whatsapp, is_active)
on table public.workshops to anon;

-- Los pedidos públicos deben pasar por el RPC validado, no por INSERT directo.
alter table public.contact_requests
add column if not exists workshop_id uuid
references public.workshops(id) on delete cascade;

alter table public.contact_requests
add column if not exists admin_notes text;

alter table public.contact_requests
add column if not exists receipt_url text;

alter table public.contact_requests
add column if not exists updated_at timestamptz not null default now();

alter table public.contact_requests
add column if not exists approved_at timestamptz;

alter table public.contact_requests
add column if not exists rejected_at timestamptz;

alter table public.contact_requests
add column if not exists completed_at timestamptz;

alter table public.contact_requests
alter column status set default 'pending';

alter table public.contact_requests
add column if not exists public_token uuid not null default gen_random_uuid();

create unique index if not exists contact_requests_public_token_key
on public.contact_requests (public_token);

create index if not exists contact_requests_phone_created_at_idx
on public.contact_requests (customer_phone, created_at desc);

drop policy if exists "public can create contact requests"
on public.contact_requests;
revoke insert on table public.contact_requests from anon;

drop policy if exists "admins can view own contact requests"
on public.contact_requests;
create policy "admins can view own contact requests"
on public.contact_requests for select
to authenticated
using (workshop_id = public.current_workshop_id());

drop policy if exists "admins can update own contact requests"
on public.contact_requests;
create policy "admins can update own contact requests"
on public.contact_requests for update
to authenticated
using (workshop_id = public.current_workshop_id())
with check (workshop_id = public.current_workshop_id());

/**
 * Crea o recupera el taller y su perfil en una sola transacción para evitar
 * talleres huérfanos cuando el registro se interrumpe entre dos peticiones.
 */
drop function if exists public.setup_admin_workshop(text, text);
create function public.setup_admin_workshop(p_name text, p_whatsapp text)
returns table (workshop_id uuid, role text)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_workshop_id uuid;
    v_name text := trim(coalesce(p_name, ''));
    v_whatsapp text := trim(coalesce(p_whatsapp, ''));
begin
    if v_user_id is null then
        raise exception 'Sesión administrativa requerida';
    end if;

    if char_length(v_name) < 2 or char_length(v_name) > 80 then
        raise exception 'Nombre de taller no válido';
    end if;

    if v_whatsapp !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'WhatsApp no válido';
    end if;

    select workshop.id into v_workshop_id
    from public.workshops workshop
    where workshop.owner_user_id = v_user_id
    for update;

    if v_workshop_id is null then
        insert into public.workshops (owner_user_id, name, whatsapp, is_active)
        values (v_user_id, v_name, v_whatsapp, true)
        returning id into v_workshop_id;
    end if;

    insert into public.profiles (user_id, workshop_id, role)
    values (v_user_id, v_workshop_id, 'admin')
    on conflict (user_id) do update
    set workshop_id = excluded.workshop_id,
        role = excluded.role;

    return query select v_workshop_id, 'admin'::text;
end;
$$;

revoke all on function public.setup_admin_workshop(text, text) from public;
grant execute on function public.setup_admin_workshop(text, text)
to authenticated;

/**
 * Crea una solicitud de catálogo sin confiar en nombres, precios o talleres
 * enviados por el cliente. Toda esa información se reconstruye en el servidor.
 */
drop function if exists public.submit_catalog_request(text, text, jsonb);
create function public.submit_catalog_request(
    p_customer_name text,
    p_customer_phone text,
    p_items jsonb
)
returns table (request_id bigint, tracking_token uuid)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_item jsonb;
    v_product public.products%rowtype;
    v_product_id bigint;
    v_quantity integer;
    v_workshop_id uuid;
    v_request_id bigint;
    v_public_token uuid;
    v_clean_items jsonb := '[]'::jsonb;
    v_seen_product_ids bigint[] := array[]::bigint[];
    v_customer_name text := trim(coalesce(p_customer_name, ''));
    v_customer_phone text := trim(coalesce(p_customer_phone, ''));
begin
    if auth.role() not in ('anon', 'authenticated') then
        raise exception 'Solicitud no autorizada';
    end if;

    if char_length(v_customer_name) < 2 or char_length(v_customer_name) > 80 then
        raise exception 'Nombre de cliente no válido';
    end if;

    if v_customer_phone !~ '^[0-9]{7,15}$' then
        raise exception 'WhatsApp no válido';
    end if;

    if exists (
        select 1
        from public.contact_requests request
        where request.customer_phone = v_customer_phone
          and request.created_at > now() - interval '60 seconds'
    ) then
        raise exception 'Espera un minuto antes de enviar otro pedido';
    end if;

    if jsonb_typeof(p_items) <> 'array'
            or jsonb_array_length(p_items) < 1
            or jsonb_array_length(p_items) > 30 then
        raise exception 'El pedido debe contener entre 1 y 30 productos';
    end if;

    for v_item in select value from jsonb_array_elements(p_items)
    loop
        begin
            v_product_id := (v_item->>'product_id')::bigint;
            v_quantity := (v_item->>'quantity')::integer;
        exception when others then
            raise exception 'Producto o cantidad no válidos';
        end;

        if v_product_id = any(v_seen_product_ids) then
            raise exception 'El pedido contiene productos duplicados';
        end if;
        v_seen_product_ids := array_append(v_seen_product_ids, v_product_id);

        if v_quantity < 1 or v_quantity > 1000 then
            raise exception 'Cantidad no válida';
        end if;

        select * into v_product
        from public.products
        where id = v_product_id and active = true;

        if not found then
            raise exception 'Producto no disponible';
        end if;

        if v_quantity > v_product.stock then
            raise exception 'Stock insuficiente para %', v_product.name;
        end if;

        if v_workshop_id is null then
            v_workshop_id := v_product.workshop_id;
        elsif v_workshop_id <> v_product.workshop_id then
            raise exception 'No se pueden mezclar talleres en un pedido';
        end if;

        v_clean_items := v_clean_items || jsonb_build_array(jsonb_build_object(
            'product_id', v_product.id,
            'product_name', v_product.name,
            'quantity', v_quantity,
            'unit_price', v_product.sale_price
        ));
    end loop;

    insert into public.contact_requests (
        workshop_id,
        customer_name,
        customer_phone,
        items,
        status
    ) values (
        v_workshop_id,
        v_customer_name,
        v_customer_phone,
        v_clean_items,
        'pending'
    ) returning id, public_token into v_request_id, v_public_token;

    return query select v_request_id, v_public_token;
end;
$$;

revoke all on function public.submit_catalog_request(text, text, jsonb)
from public;
grant execute on function public.submit_catalog_request(text, text, jsonb)
to anon, authenticated;

-- El número de teléfono deja de funcionar como llave pública de consulta.
do $$
begin
    if to_regprocedure('public.get_catalog_orders_by_phone(text)') is not null then
        execute 'revoke all on function public.get_catalog_orders_by_phone(text) from public, anon, authenticated';
    end if;
end;
$$;

drop function if exists public.get_catalog_orders_by_tokens(jsonb);
create function public.get_catalog_orders_by_tokens(p_tracking_tokens jsonb)
returns table (
    id bigint,
    workshop_id uuid,
    customer_name text,
    customer_phone text,
    items jsonb,
    status text,
    ai_description text,
    created_at timestamptz,
    updated_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    completed_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.role() not in ('anon', 'authenticated') then
        raise exception 'Solicitud no autorizada';
    end if;

    if jsonb_typeof(p_tracking_tokens) <> 'array'
            or jsonb_array_length(p_tracking_tokens) < 1
            or jsonb_array_length(p_tracking_tokens) > 20 then
        raise exception 'Códigos de seguimiento no válidos';
    end if;

    return query
    select
        request.id,
        request.workshop_id,
        request.customer_name,
        request.customer_phone,
        request.items,
        request.status,
        request.ai_description,
        request.created_at,
        request.updated_at,
        request.approved_at,
        request.rejected_at,
        request.completed_at
    from public.contact_requests request
    where request.public_token in (
        select (token.value #>> '{}')::uuid
        from jsonb_array_elements(p_tracking_tokens) token(value)
    )
    order by request.created_at desc;
exception when invalid_text_representation then
    raise exception 'Código de seguimiento no válido';
end;
$$;

revoke all on function public.get_catalog_orders_by_tokens(jsonb)
from public;
grant execute on function public.get_catalog_orders_by_tokens(jsonb)
to anon, authenticated;

-- Una salida de inventario nunca puede descontar más unidades de las existentes.
create or replace function public.adjust_stock(
    p_product_id bigint,
    p_movement_type text,
    p_quantity integer,
    p_reason text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_workshop_id uuid := public.current_workshop_id();
    v_previous integer;
    v_new integer;
begin
    if v_workshop_id is null then
        raise exception 'Usuario sin taller';
    end if;

    if p_quantity <= 0 then
        raise exception 'Cantidad inválida';
    end if;

    select stock into v_previous
    from public.products
    where id = p_product_id and workshop_id = v_workshop_id
    for update;

    if v_previous is null then
        raise exception 'Producto no encontrado';
    end if;

    if p_movement_type = 'Entrada' then
        v_new := v_previous + p_quantity;
    elsif p_movement_type = 'Salida' then
        if v_previous < p_quantity then
            raise exception 'Stock insuficiente';
        end if;
        v_new := v_previous - p_quantity;
    elsif p_movement_type = 'Ajuste' then
        v_new := p_quantity;
    else
        raise exception 'Tipo de movimiento inválido';
    end if;

    update public.products
    set stock = v_new, updated_at = now()
    where id = p_product_id and workshop_id = v_workshop_id;

    insert into public.inventory_movements (
        workshop_id, product_id, movement_type, quantity,
        previous_stock, new_stock, reason
    ) values (
        v_workshop_id, p_product_id, p_movement_type, p_quantity,
        v_previous, v_new, p_reason
    );
end;
$$;

-- Los RPC administrativos permanecen disponibles solo para usuarios autenticados.
revoke all on function public.adjust_stock(bigint, text, integer, text)
from public;
grant execute on function public.adjust_stock(bigint, text, integer, text)
to authenticated;

revoke all on function public.confirm_sale(text, text, text, integer, text, jsonb)
from public;
grant execute on function public.confirm_sale(text, text, text, integer, text, jsonb)
to authenticated;

-- Cada taller administra exclusivamente su propia carpeta de imágenes.
drop policy if exists "authenticated can upload product images"
on storage.objects;
drop policy if exists "workshops can upload own product images"
on storage.objects;
create policy "workshops can upload own product images"
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'product-images'
    and split_part(name, '/', 1) = public.current_workshop_id()::text
);

drop policy if exists "authenticated can update product images"
on storage.objects;
drop policy if exists "workshops can update own product images"
on storage.objects;
create policy "workshops can update own product images"
on storage.objects for update
to authenticated
using (
    bucket_id = 'product-images'
    and split_part(name, '/', 1) = public.current_workshop_id()::text
)
with check (
    bucket_id = 'product-images'
    and split_part(name, '/', 1) = public.current_workshop_id()::text
);

drop policy if exists "authenticated can delete product images"
on storage.objects;
drop policy if exists "workshops can delete own product images"
on storage.objects;
create policy "workshops can delete own product images"
on storage.objects for delete
to authenticated
using (
    bucket_id = 'product-images'
    and split_part(name, '/', 1) = public.current_workshop_id()::text
);

-- Los comprobantes son privados y solo pertenecen al taller de su carpeta.
insert into storage.buckets (id, name, public)
values ('order-receipts', 'order-receipts', false)
on conflict (id) do update set public = false;

drop policy if exists "workshops can upload own order receipts"
on storage.objects;
create policy "workshops can upload own order receipts"
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'order-receipts'
    and split_part(name, '/', 1) = public.current_workshop_id()::text
);

drop policy if exists "workshops can view own order receipts"
on storage.objects;
create policy "workshops can view own order receipts"
on storage.objects for select
to authenticated
using (
    bucket_id = 'order-receipts'
    and split_part(name, '/', 1) = public.current_workshop_id()::text
);

drop policy if exists "workshops can delete own order receipts"
on storage.objects;
create policy "workshops can delete own order receipts"
on storage.objects for delete
to authenticated
using (
    bucket_id = 'order-receipts'
    and split_part(name, '/', 1) = public.current_workshop_id()::text
);

commit;
