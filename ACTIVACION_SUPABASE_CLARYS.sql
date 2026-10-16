-- Clarys: actualizacion completa, generada desde las migraciones versionadas.
-- Ejecutar en Supabase SQL Editor. Toda la actualizacion es una transaccion.
begin;
-- Clarys - endurecimiento previo a producción.
-- Revisar en un entorno de prueba antes de ejecutar en el proyecto principal.



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



-- Ejecutar DESPUES de 20260912_production_hardening.sql.
-- Stock representa unidades fisicas; reserved_stock se deriva del libro de reservas.


alter table public.sales alter column total type bigint;
alter table public.sale_items alter column subtotal type bigint;
alter table public.customers alter column total_spent type bigint;

alter table public.products add column if not exists reserved_stock integer not null default 0;
alter table public.products add column if not exists variants jsonb not null default '[]';
alter table public.contact_requests add column if not exists sale_id bigint references public.sales(id);
alter table public.sales add column if not exists request_id bigint unique references public.contact_requests(id);
alter table public.contact_requests add column if not exists submission_key uuid unique;
alter table public.contact_requests add column if not exists submission_fingerprint text;
alter table public.sales add column if not exists submission_key uuid unique;
alter table public.sales add column if not exists submission_fingerprint text;
alter table public.sale_items add column if not exists unit_cost integer not null default 0;
alter table public.sale_items add column if not exists product_name text;
alter table public.sale_items add column if not exists variant_id text;
alter table public.sale_items add column if not exists variant_label text;
alter table public.contact_requests drop constraint if exists contact_requests_sale_id_fkey;
alter table public.contact_requests add constraint contact_requests_sale_id_fkey
    foreign key(sale_id) references public.sales(id) on delete set null;
alter table public.sales drop constraint if exists sales_request_id_fkey;
alter table public.sales add constraint sales_request_id_fkey
    foreign key(request_id) references public.contact_requests(id) on delete set null;

create table if not exists public.order_reservations (
    request_id bigint not null references public.contact_requests(id) on delete cascade,
    product_id bigint not null references public.products(id) on delete restrict,
    variant_id text not null default '',
    quantity integer not null check(quantity > 0),
    primary key(request_id, product_id, variant_id)
);
create table if not exists public.order_events (
    id bigint generated by default as identity primary key,
    request_id bigint not null references public.contact_requests(id) on delete cascade,
    workshop_id uuid not null references public.workshops(id) on delete cascade,
    previous_status text,
    status text not null,
    actor_id uuid,
    created_at timestamptz not null default now()
);
alter table public.order_reservations enable row level security;
alter table public.order_events enable row level security;
revoke all on public.order_reservations from anon, authenticated;
revoke all on public.order_events from anon, authenticated;
grant select on public.order_events to authenticated;
drop policy if exists "owner reads events" on public.order_events;
create policy "owner reads events" on public.order_events for select to authenticated
using(workshop_id = public.current_workshop_id());
grant select(reserved_stock, variants) on public.products to anon;

-- El catalogo anonimo y la administracion usan politicas diferentes.
drop policy if exists "public can view active products" on public.products;
create policy "public can view active products" on public.products for select to anon using(active);
drop policy if exists "public can view active workshops" on public.workshops;
create policy "public can view active workshops" on public.workshops for select to anon using(is_active);
drop policy if exists "owner reads workshop" on public.workshops;
create policy "owner reads workshop" on public.workshops for select to authenticated using(owner_user_id=auth.uid());
revoke insert,update,delete on public.profiles from anon,authenticated;

-- Toda escritura de producto valida existencias y reconstruye reservas desde el libro.
create or replace function public.clarys_product_guard()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    v_variant jsonb;
    v_variants jsonb := '[]';
    v_seen text[] := '{}';
    v_combinations text[] := '{}';
    v_id text;
    v_key text;
    v_stock integer;
    v_reserved integer;
    v_total integer := 0;
begin
    select coalesce(sum(quantity),0) into new.reserved_stock
    from public.order_reservations where product_id = new.id;
    if new.stock < new.reserved_stock or new.stock>1000000 then
        raise exception 'No puedes reducir el stock por debajo de las unidades reservadas';
    end if;
    if new.purchase_price < 0 or new.sale_price < 0 then
        raise exception 'Los precios no pueden ser negativos';
    end if;
    if jsonb_typeof(new.variants) <> 'array' or jsonb_array_length(new.variants) > 100 then
        raise exception 'Variantes no validas';
    end if;
    for v_variant in select value from jsonb_array_elements(new.variants) loop
        v_id := trim(coalesce(v_variant->>'id',''));
        v_key := lower(trim(coalesce(v_variant->>'size',''))) || ':' ||
                 lower(trim(coalesce(v_variant->>'color','')));
        if v_id = '' or v_id = any(v_seen) or v_key = any(v_combinations)
           or length(v_id) > 100 or length(v_variant->>'size') > 40
           or length(v_variant->>'color') > 60 then
            raise exception 'Identificador o combinacion de variante no valido';
        end if;
        v_seen := array_append(v_seen,v_id);
        v_combinations := array_append(v_combinations,v_key);
        v_stock := (v_variant->>'stock')::integer;
        select coalesce(sum(quantity),0) into v_reserved
        from public.order_reservations where product_id = new.id and variant_id = v_id;
        if v_stock is null or v_stock < v_reserved or v_stock > 1000000 then
            raise exception 'Stock no valido para talla/color';
        end if;
        v_total := v_total + v_stock;
        v_variants := v_variants || jsonb_build_array(jsonb_build_object(
            'id',v_id,'size',trim(coalesce(v_variant->>'size','')),
            'color',trim(coalesce(v_variant->>'color','')),
            'stock',v_stock,'reserved_stock',v_reserved));
    end loop;
    if jsonb_array_length(new.variants) > 0 then
        if exists(select 1 from public.order_reservations
                  where product_id = new.id and not(variant_id = any(v_seen))) then
            raise exception 'No puedes quitar una variante reservada';
        end if;
        new.stock := v_total;
        if v_total>1000000 then raise exception 'El total de variantes supera el limite de stock'; end if;
        new.variants := v_variants;
    elsif exists(select 1 from public.order_reservations
                 where product_id = new.id and variant_id <> '') then
        raise exception 'No puedes quitar variantes reservadas';
    end if;
    new.updated_at := now();
    return new;
end $$;
drop trigger if exists clarys_product_guard on public.products;
create trigger clarys_product_guard before insert or update on public.products
for each row execute function public.clarys_product_guard();

create or replace function public.clarys_stock_audit()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_previous integer:=0; v_reason text;
begin
    if tg_op='UPDATE' then v_previous:=old.stock; end if;
    if new.stock=v_previous then return new; end if;
    v_reason:=nullif(current_setting('clarys.movement_reason',true),'');
    insert into public.inventory_movements(workshop_id,product_id,movement_type,quantity,previous_stock,new_stock,reason)
    values(new.workshop_id,new.id,
        case when tg_op='INSERT' then 'Entrada' when v_reason is null then 'Ajuste'
             when new.stock>v_previous then 'Entrada' else 'Salida' end,
        abs(new.stock-v_previous),v_previous,new.stock,
        coalesce(v_reason,case when tg_op='INSERT' then 'Creacion de producto' else 'Edicion de producto o variantes' end));
    return new;
end $$;
drop trigger if exists clarys_stock_audit on public.products;
create trigger clarys_stock_audit after insert or update on public.products
for each row execute function public.clarys_stock_audit();

-- Funcion interna. Las operaciones de pedidos/ventas bloquean siempre el producto.
create or replace function public.clarys_change_units(p_product_id bigint, p_variant_id text,
                                                      p_delta integer, p_sold integer default 0)
returns void language plpgsql security definer set search_path = '' as $$
declare v_product public.products%rowtype; v_variants jsonb; v_found boolean;
begin
    select * into strict v_product from public.products where id=p_product_id for update;
    if coalesce(p_variant_id,'') <> '' then
        select exists(select 1 from jsonb_array_elements(v_product.variants) v
                      where v->>'id'=p_variant_id) into v_found;
        if not v_found then raise exception 'Variante no encontrada'; end if;
        select jsonb_agg(case when v->>'id'=p_variant_id then
            jsonb_set(v,'{stock}',to_jsonb((v->>'stock')::integer+p_delta)) else v end order by n)
        into v_variants from jsonb_array_elements(v_product.variants) with ordinality a(v,n);
        update public.products set variants=v_variants,stock=stock+p_delta,
            sold_units=sold_units+p_sold where id=p_product_id;
    else
        if jsonb_array_length(v_product.variants)>0 then
            raise exception 'Selecciona la talla y el color del producto';
        end if;
        update public.products set stock=stock+p_delta,sold_units=sold_units+p_sold
        where id=p_product_id;
    end if;
end $$;
revoke all on function public.clarys_change_units(bigint,text,integer,integer) from public,anon,authenticated;

-- Una transicion realiza la reserva, liberacion o entrega como una sola transaccion.
create or replace function public.clarys_order_transition()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    v_item jsonb; v_product public.products%rowtype; v_variant jsonb;
    v_quantity integer; v_variant_id text; v_reserved integer; v_available integer;
    v_customer bigint; v_sale bigint; v_total bigint := 0;
begin
    if new.items is distinct from old.items or new.workshop_id is distinct from old.workshop_id then
        raise exception 'Los articulos y el taller del pedido no pueden cambiar';
    end if;
    if new.sale_id is distinct from old.sale_id
        and not(new.sale_id is null and not exists(select 1 from public.sales where id=old.sale_id)) then
        raise exception 'La venta vinculada se establece automaticamente';
    end if;
    if new.status = old.status then return new; end if;
    if old.status in ('completed','rejected') then raise exception 'El pedido ya esta cerrado'; end if;
    if not (new.status='rejected' or (old.status='pending' and new.status='contacted')
        or (old.status='contacted' and new.status='approved')
        or (old.status='approved' and new.status='preparing')
        or (old.status='preparing' and new.status='completed')) then
        raise exception 'Transicion de pedido no valida';
    end if;
    -- Orden de bloqueo estable para evitar interbloqueos entre pedidos.
    perform id from public.products where id in
        (select (value->>'product_id')::bigint from jsonb_array_elements(new.items))
        order by id for update;
    if new.status in ('approved','preparing') then
        for v_item in select value from jsonb_array_elements(new.items) loop
            v_quantity := (v_item->>'quantity')::integer;
            v_variant_id := coalesce(v_item->>'variant_id','');
            if v_quantity is null or v_quantity<1 then raise exception 'Cantidad no valida'; end if;
            select * into v_product from public.products
                where id=(v_item->>'product_id')::bigint and workshop_id=new.workshop_id and active;
            if not found then raise exception 'Producto no disponible'; end if;
            if not exists(select 1 from public.order_reservations where request_id=new.id
                and product_id=v_product.id and variant_id=v_variant_id) then
                v_available := v_product.stock-v_product.reserved_stock;
                if jsonb_array_length(v_product.variants)>0 then
                    select value into v_variant from jsonb_array_elements(v_product.variants)
                        where value->>'id'=v_variant_id;
                    if not found then raise exception 'Elige talla/color para %',v_product.name; end if;
                    v_available := (v_variant->>'stock')::integer-(v_variant->>'reserved_stock')::integer;
                elsif v_variant_id<>'' then raise exception 'Variante no disponible'; end if;
                if v_quantity>v_available then raise exception 'Stock insuficiente para %',v_product.name; end if;
                insert into public.order_reservations values(new.id,v_product.id,v_variant_id,v_quantity);
                update public.products set stock=stock where id=v_product.id;
            end if;
        end loop;
    elsif new.status='rejected' then
        delete from public.order_reservations where request_id=new.id;
        update public.products set stock=stock where id in
            (select (value->>'product_id')::bigint from jsonb_array_elements(new.items));
        new.rejected_at := now();
    elsif new.status='completed' then
        if exists(select 1 from public.sales where request_id=new.id) then
            raise exception 'El pedido ya tiene una venta registrada';
        end if;
        perform pg_advisory_xact_lock(hashtextextended(new.workshop_id::text||':'||new.customer_phone,3));
        select id into v_customer from public.customers where workshop_id=new.workshop_id and phone=new.customer_phone
            order by id limit 1 for update;
        if v_customer is null then
            insert into public.customers(workshop_id,name,phone) values
                (new.workshop_id,new.customer_name,new.customer_phone) returning id into v_customer;
        end if;
        insert into public.sales(workshop_id,customer_id,payment_method,status,request_id)
            values(new.workshop_id,v_customer,'Acordado con el cliente','Confirmada',new.id) returning id into v_sale;
        for v_item in select value from jsonb_array_elements(new.items) loop
            v_quantity := (v_item->>'quantity')::integer;
            v_variant_id := coalesce(v_item->>'variant_id','');
            select * into strict v_product from public.products where id=(v_item->>'product_id')::bigint;
            select quantity into v_reserved from public.order_reservations
                where request_id=new.id and product_id=v_product.id and variant_id=v_variant_id;
            if v_reserved is null or v_reserved<>v_quantity then raise exception 'Reserva no encontrada'; end if;
            delete from public.order_reservations where request_id=new.id and product_id=v_product.id and variant_id=v_variant_id;
            perform set_config('clarys.movement_reason','Entrega pedido #'||new.id,true);
            perform public.clarys_change_units(v_product.id,v_variant_id,-v_quantity,v_quantity);
            perform set_config('clarys.movement_reason','',true);
            insert into public.sale_items(sale_id,product_id,quantity,unit_price,subtotal,unit_cost,product_name,variant_id,variant_label)
                values(v_sale,v_product.id,v_quantity,(v_item->>'unit_price')::integer,
                    v_quantity::bigint*(v_item->>'unit_price')::integer,v_product.purchase_price,
                    v_item->>'product_name',v_variant_id,v_item->>'variant_label');
            v_total := v_total+v_quantity::bigint*(v_item->>'unit_price')::integer;
        end loop;
        update public.sales set total=v_total where id=v_sale;
        update public.customers set total_orders=total_orders+1,total_spent=total_spent+v_total where id=v_customer;
        new.sale_id := v_sale;
        new.completed_at := now();
    end if;
    if new.status='approved' then new.approved_at := now(); end if;
    new.updated_at := now();
    return new;
end $$;
drop trigger if exists clarys_order_transition on public.contact_requests;
create trigger clarys_order_transition before update on public.contact_requests
for each row execute function public.clarys_order_transition();

create or replace function public.clarys_order_event()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if tg_op='INSERT' then
        insert into public.order_events(request_id,workshop_id,status,actor_id)
            values(new.id,new.workshop_id,new.status,auth.uid());
    elsif new.status is distinct from old.status then
        insert into public.order_events(request_id,workshop_id,previous_status,status,actor_id)
            values(new.id,new.workshop_id,old.status,new.status,auth.uid());
    end if;
    return new;
end $$;
drop trigger if exists clarys_order_event on public.contact_requests;
create trigger clarys_order_event after insert or update on public.contact_requests
for each row execute function public.clarys_order_event();

create or replace function public.clarys_order_deleted()
returns trigger language plpgsql security definer set search_path='' as $$
begin
    -- Al borrar un pedido tambien desaparece su libro de reservas por FK.
    update public.products set stock=stock where id in
        (select (value->>'product_id')::bigint from jsonb_array_elements(old.items));
    return old;
end $$;
drop trigger if exists clarys_order_deleted on public.contact_requests;
create trigger clarys_order_deleted after delete on public.contact_requests
for each row execute function public.clarys_order_deleted();

-- Auth conserva su API de eliminacion; este hook elimina dependencias comerciales en orden.
create or replace function public.clarys_user_deleted()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_workshop uuid;
begin
    for v_workshop in select id from public.workshops where owner_user_id=old.id loop
        delete from public.sale_items where sale_id in(select id from public.sales where workshop_id=v_workshop);
        delete from public.sales where workshop_id=v_workshop;
        delete from public.contact_requests where workshop_id=v_workshop;
        delete from public.products where workshop_id=v_workshop;
    end loop;
    return old;
end $$;
drop trigger if exists clarys_user_deleted on auth.users;
create trigger clarys_user_deleted before delete on auth.users
for each row execute function public.clarys_user_deleted();

create or replace function public.submit_catalog_request(p_customer_name text,p_customer_phone text,p_items jsonb,p_submission_key uuid)
returns table(request_id bigint,tracking_token uuid)
language plpgsql security definer set search_path = '' as $$
declare v_item jsonb; v_product public.products%rowtype; v_variant jsonb;
    v_quantity integer; v_available integer; v_variant_id text; v_key text;
    v_workshop uuid; v_seen text[] := '{}'; v_items jsonb := '[]'; v_id bigint; v_token uuid;
    v_previous public.contact_requests%rowtype;
    v_fingerprint text := md5(p_customer_name||':'||p_customer_phone||':'||p_items::text);
begin
    if auth.role() not in ('anon','authenticated') then raise exception 'Solicitud no autorizada'; end if;
    if p_customer_name is null or length(trim(p_customer_name)) not between 2 and 80 then raise exception 'Nombre no valido'; end if;
    if p_customer_phone is null or p_customer_phone !~ '^[0-9]{7,15}$' then raise exception 'WhatsApp no valido'; end if;
    if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 30 then
        raise exception 'El pedido debe contener entre 1 y 30 articulos';
    end if;
    -- Serializa solicitudes repetidas del mismo telefono sin almacenar datos adicionales.
    perform pg_advisory_xact_lock(hashtextextended(p_customer_phone,0));
    if p_submission_key is not null then
        perform pg_advisory_xact_lock(hashtextextended(p_submission_key::text,1));
        select * into v_previous from public.contact_requests where submission_key=p_submission_key;
        if found then
            if v_previous.submission_fingerprint is distinct from v_fingerprint then
                raise exception 'La solicitud de reintento no coincide con el pedido original';
            end if;
            return query select v_previous.id,v_previous.public_token;
            return;
        end if;
    end if;
    if exists(select 1 from public.contact_requests where customer_phone=p_customer_phone
              and created_at>now()-interval '60 seconds') then raise exception 'Espera un minuto antes de enviar otro pedido'; end if;
    for v_item in select value from jsonb_array_elements(p_items) loop
        v_quantity := (v_item->>'quantity')::integer;
        v_variant_id := coalesce(v_item->>'variant_id','');
        v_key := (v_item->>'product_id')||':'||v_variant_id;
        if v_key is null or v_key=any(v_seen) then raise exception 'Articulo duplicado o invalido'; end if;
        v_seen := array_append(v_seen,v_key);
        if v_quantity is null or v_quantity not between 1 and 1000 then raise exception 'Cantidad no valida'; end if;
        select * into v_product from public.products where id=(v_item->>'product_id')::bigint and active;
        if not found then raise exception 'Producto no disponible'; end if;
        if not exists(select 1 from public.workshops where id=v_product.workshop_id and is_active) then
            raise exception 'Taller no disponible';
        end if;
        v_available := v_product.stock-v_product.reserved_stock;
        v_variant := null;
        if jsonb_array_length(v_product.variants)>0 then
            select value into v_variant from jsonb_array_elements(v_product.variants) where value->>'id'=v_variant_id;
            if not found then raise exception 'Selecciona talla y color'; end if;
            v_available := (v_variant->>'stock')::integer-(v_variant->>'reserved_stock')::integer;
        elsif v_variant_id<>'' then raise exception 'Variante no disponible'; end if;
        if v_quantity>v_available then raise exception 'Stock insuficiente para %',v_product.name; end if;
        if (v_item->>'unit_price')::integer is distinct from v_product.sale_price then
            raise exception 'El precio cambio; actualiza el carrito';
        end if;
        if v_workshop is null then v_workshop:=v_product.workshop_id;
        elsif v_workshop<>v_product.workshop_id then raise exception 'No puedes mezclar talleres'; end if;
        v_items := v_items || jsonb_build_array(jsonb_build_object(
            'product_id',v_product.id,'product_name',v_product.name,'quantity',v_quantity,
            'unit_price',v_product.sale_price,'variant_id',v_variant_id,
            'variant_label',case when v_variant is null then '' else
                (v_variant->>'size')||' · '||(v_variant->>'color') end));
    end loop;
    insert into public.contact_requests(workshop_id,customer_name,customer_phone,items,status,submission_key,submission_fingerprint)
        values(v_workshop,trim(p_customer_name),p_customer_phone,v_items,'pending',p_submission_key,v_fingerprint)
        returning id,public_token into v_id,v_token;
    return query select v_id,v_token;
end $$;
revoke all on function public.submit_catalog_request(text,text,jsonb,uuid) from public;
grant execute on function public.submit_catalog_request(text,text,jsonb,uuid) to anon,authenticated;
create or replace function public.submit_catalog_request(p_customer_name text,p_customer_phone text,p_items jsonb)
returns table(request_id bigint,tracking_token uuid)
language sql security definer set search_path='' as $$
    select * from public.submit_catalog_request(p_customer_name,p_customer_phone,p_items,null::uuid);
$$;

-- Mantiene el RPC anterior para productos sin variantes y agrega uno especifico.
create or replace function public.adjust_variant_stock(p_product_id bigint,p_variant_id text,
    p_movement_type text,p_quantity integer,p_reason text)
returns void language plpgsql security definer set search_path = '' as $$
declare v_product public.products%rowtype; v_variant jsonb; v_previous integer; v_next integer; v_delta integer;
begin
    if public.current_workshop_id() is null then raise exception 'Sesion requerida'; end if;
    if p_quantity is null or p_quantity<0 or p_quantity>1000000
        or (p_movement_type<>'Ajuste' and p_quantity=0) then raise exception 'Cantidad no valida'; end if;
    select * into v_product from public.products
        where id=p_product_id and workshop_id=public.current_workshop_id() for update;
    if not found then raise exception 'Producto no encontrado'; end if;
    v_previous:=v_product.stock;
    if coalesce(p_variant_id,'')<>'' then
        select value into v_variant from jsonb_array_elements(v_product.variants) where value->>'id'=p_variant_id;
        if not found then raise exception 'Variante no encontrada'; end if;
        v_previous := (v_variant->>'stock')::integer;
    end if;
    if p_movement_type='Entrada' then v_next:=v_previous+p_quantity;
    elsif p_movement_type='Salida' then v_next:=v_previous-p_quantity;
    elsif p_movement_type='Ajuste' then v_next:=p_quantity;
    else raise exception 'Movimiento no valido'; end if;
    v_delta:=v_next-v_previous;
    perform set_config('clarys.movement_reason',coalesce(nullif(p_reason,''),'Movimiento manual')||
        case when v_variant is null then '' else ' ['||(v_variant->>'size')||'/'||(v_variant->>'color')||']' end,true);
    perform public.clarys_change_units(p_product_id,coalesce(p_variant_id,''),v_delta,0);
    perform set_config('clarys.movement_reason','',true);
end $$;
revoke all on function public.adjust_variant_stock(bigint,text,text,integer,text) from public;
grant execute on function public.adjust_variant_stock(bigint,text,text,integer,text) to authenticated;
create or replace function public.adjust_stock(p_product_id bigint,p_movement_type text,p_quantity integer,p_reason text)
returns void language plpgsql security definer set search_path = '' as $$
begin
    perform public.adjust_variant_stock(p_product_id,'',p_movement_type,p_quantity,p_reason);
end $$;

create or replace function public.get_clarys_capabilities()
returns jsonb language sql stable set search_path = '' as $$
    select '{"version":2,"reservations":true,"variants":true,"order_sales":true}'::jsonb;
$$;
grant execute on function public.get_clarys_capabilities() to anon,authenticated;

-- La venta de mostrador comparte las mismas validaciones que la entrega de pedidos.
create or replace function public.confirm_sale(p_customer_name text,p_customer_phone text,
    p_payment_method text,p_discount integer,p_status text,p_items jsonb,p_submission_key uuid)
returns bigint language plpgsql security definer set search_path='' as $$
declare v_workshop uuid:=public.current_workshop_id(); v_customer bigint; v_sale bigint;
    v_product public.products%rowtype; v_variant jsonb; v_item jsonb; v_quantity integer;
    v_variant_id text; v_available integer; v_total bigint:=0; v_seen text[]:='{}'; v_key text;
    v_fingerprint text:=md5(coalesce(p_customer_name,'')||':'||coalesce(p_customer_phone,'')||':'||
        coalesce(p_payment_method,'')||':'||p_discount::text||':'||coalesce(p_status,'')||':'||p_items::text);
    v_previous_fingerprint text;
begin
    if v_workshop is null then raise exception 'Sesion requerida'; end if;
    if p_submission_key is not null then
        perform pg_advisory_xact_lock(hashtextextended(p_submission_key::text,2));
        select id,submission_fingerprint into v_sale,v_previous_fingerprint
            from public.sales where submission_key=p_submission_key and workshop_id=v_workshop;
        if found then
            if v_previous_fingerprint is distinct from v_fingerprint then
                raise exception 'El reintento no coincide con la venta original';
            end if;
            return v_sale;
        end if;
    end if;
    if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 30
        or p_discount is null or p_discount<0 then raise exception 'Datos de venta no validos'; end if;
    perform id from public.products where id in
        (select (value->>'product_id')::bigint from jsonb_array_elements(p_items)) order by id for update;
    perform pg_advisory_xact_lock(hashtextextended(v_workshop::text||':'||coalesce(p_customer_phone,''),3));
    if coalesce(p_customer_phone,'')<>'' then
        select id into v_customer from public.customers where workshop_id=v_workshop and phone=p_customer_phone
            order by id limit 1 for update;
    end if;
    if v_customer is null then
        insert into public.customers(workshop_id,name,phone) values(v_workshop,coalesce(nullif(p_customer_name,''),'Cliente'),p_customer_phone)
            returning id into v_customer;
    end if;
    insert into public.sales(workshop_id,customer_id,payment_method,status,discount,submission_key,submission_fingerprint)
        values(v_workshop,v_customer,coalesce(p_payment_method,'Pendiente'),coalesce(p_status,'Confirmada'),p_discount,p_submission_key,v_fingerprint)
        returning id into v_sale;
    for v_item in select value from jsonb_array_elements(p_items) loop
        v_quantity:=(v_item->>'quantity')::integer; v_variant_id:=coalesce(v_item->>'variant_id','');
        v_key:=(v_item->>'product_id')||':'||v_variant_id;
        if v_key is null or v_key=any(v_seen) or v_quantity is null or v_quantity not between 1 and 1000 then
            raise exception 'Articulo de venta no valido';
        end if;
        v_seen:=array_append(v_seen,v_key);
        select * into v_product from public.products where id=(v_item->>'product_id')::bigint and workshop_id=v_workshop and active;
        if not found then raise exception 'Producto no disponible'; end if;
        v_available:=v_product.stock-v_product.reserved_stock; v_variant:=null;
        if jsonb_array_length(v_product.variants)>0 then
            select value into v_variant from jsonb_array_elements(v_product.variants) where value->>'id'=v_variant_id;
            if not found then raise exception 'Selecciona talla y color'; end if;
            v_available:=(v_variant->>'stock')::integer-(v_variant->>'reserved_stock')::integer;
        elsif v_variant_id<>'' then raise exception 'Variante no disponible'; end if;
        if v_quantity>v_available then raise exception 'Stock insuficiente'; end if;
        perform set_config('clarys.movement_reason','Venta #'||v_sale,true);
        perform public.clarys_change_units(v_product.id,v_variant_id,-v_quantity,v_quantity);
        perform set_config('clarys.movement_reason','',true);
        insert into public.sale_items(sale_id,product_id,product_name,quantity,unit_price,unit_cost,subtotal,variant_id,variant_label)
        values(v_sale,v_product.id,v_product.name,v_quantity,v_product.sale_price,v_product.purchase_price,
               v_quantity::bigint*v_product.sale_price,v_variant_id,
               case when v_variant is null then '' else (v_variant->>'size')||' · '||(v_variant->>'color') end);
        v_total:=v_total+v_quantity::bigint*v_product.sale_price;
    end loop;
    if p_discount>v_total then raise exception 'El descuento supera el valor de la venta'; end if;
    update public.sales set total=v_total-p_discount where id=v_sale;
    update public.customers set total_orders=total_orders+1,total_spent=total_spent+v_total-p_discount where id=v_customer;
    return v_sale;
end $$;
revoke all on function public.confirm_sale(text,text,text,integer,text,jsonb,uuid) from public;
grant execute on function public.confirm_sale(text,text,text,integer,text,jsonb,uuid) to authenticated;
create or replace function public.confirm_sale(p_customer_name text,p_customer_phone text,
    p_payment_method text,p_discount integer,p_status text,p_items jsonb)
returns bigint language sql security definer set search_path='' as $$
    select public.confirm_sale(p_customer_name,p_customer_phone,p_payment_method,p_discount,p_status,p_items,null::uuid);
$$;

-- Reportes antiguos conservan sus totales, nuevos registros guardan precios/costos historicos.
update public.sale_items item set product_name=product.name,unit_cost=product.purchase_price
from public.products product where item.product_id=product.id and item.product_name is null;
notify pgrst, 'reload schema';


-- Compatibilidad con proyectos que conservan el CHECK antiguo de cuatro estados.
-- No cambia filas, productos, usuarios, permisos ni el historial de pedidos.

set local lock_timeout = '5s';

alter table public.contact_requests
    drop constraint if exists contact_requests_status_check;
alter table public.contact_requests
    add constraint contact_requests_status_check
    check (status in ('pending', 'contacted', 'approved', 'preparing', 'completed', 'rejected'));

create or replace function public.get_clarys_capabilities()
returns jsonb language sql stable set search_path = '' as $$
    select '{"version":2,"reservations":true,"variants":true,"order_sales":true,
      "order_statuses":["pending","contacted","approved","preparing","completed","rejected"]}'::jsonb;
$$;
grant execute on function public.get_clarys_capabilities() to anon, authenticated;
notify pgrst, 'reload schema';


commit;
