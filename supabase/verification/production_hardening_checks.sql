-- Clarys - comprobaciones de solo lectura posteriores a la migración.
-- Cada fila debe devolver el valor esperado indicado en su nombre.

select
    has_column_privilege('anon', 'public.products', 'sale_price', 'select')
        as anon_can_read_sale_price_expected_true,
    has_column_privilege('anon', 'public.products', 'purchase_price', 'select')
        as anon_can_read_purchase_price_expected_false,
    has_column_privilege('anon', 'public.workshops', 'name', 'select')
        as anon_can_read_workshop_name_expected_true,
    has_column_privilege('anon', 'public.workshops', 'owner_user_id', 'select')
        as anon_can_read_owner_user_id_expected_false;

select
    has_function_privilege(
        'anon',
        'public.submit_catalog_request(text,text,jsonb)',
        'execute'
    ) as anon_can_submit_catalog_request_expected_true,
    has_function_privilege(
        'anon',
        'public.get_catalog_orders_by_tokens(jsonb)',
        'execute'
    ) as anon_can_query_orders_by_token_expected_true,
    has_function_privilege(
        'anon',
        'public.adjust_stock(bigint,text,integer,text)',
        'execute'
    ) as anon_can_adjust_stock_expected_false,
    has_function_privilege(
        'authenticated',
        'public.adjust_stock(bigint,text,integer,text)',
        'execute'
    ) as authenticated_can_adjust_stock_expected_true;

select
    policyname,
    cmd,
    roles
from pg_policies
where schemaname in ('public', 'storage')
  and tablename in ('contact_requests', 'objects')
order by schemaname, tablename, policyname;

select id, name, public
from storage.buckets
where id in ('product-images', 'order-receipts')
order by id;
