-- Ejecutar en SQL Editor como administrador. Solo lectura; no devuelve filas de usuarios.
begin read only;
select jsonb_build_object(
    'captured_at', now(),
    'postgres_version', version(),
    'database', current_database(),
    'tables', (select coalesce(jsonb_agg(jsonb_build_object(
        'schema', t.table_schema, 'name', t.table_name,
        'columns', (select jsonb_agg(jsonb_build_object(
            'name', c.column_name, 'type', c.data_type, 'udt', c.udt_name,
            'nullable', c.is_nullable, 'default', c.column_default) order by c.ordinal_position)
            from information_schema.columns c where c.table_schema=t.table_schema and c.table_name=t.table_name)
        ) order by t.table_schema,t.table_name),'[]'::jsonb)
        from information_schema.tables t where t.table_schema in ('public','auth','storage')
        and t.table_type='BASE TABLE'),
    'functions', (select coalesce(jsonb_agg(jsonb_build_object(
        'schema', n.nspname, 'name', p.proname, 'arguments', pg_get_function_identity_arguments(p.oid),
        'result', pg_get_function_result(p.oid), 'security_definer', p.prosecdef,
        'definition_md5', md5(pg_get_functiondef(p.oid))) order by p.proname),'[]'::jsonb)
        from pg_proc p join pg_namespace n on n.oid=p.pronamespace
        where n.nspname='public' and p.prokind='f'),
    'policies', (select coalesce(jsonb_agg(jsonb_build_object(
        'schema', schemaname,'table', tablename,'policy',policyname,'command',cmd,
        'roles',roles,'using',qual,'check',with_check) order by schemaname,tablename,policyname),'[]'::jsonb)
        from pg_policies where schemaname in ('public','auth','storage')),
    'constraints', (select coalesce(jsonb_agg(jsonb_build_object(
        'table', c.conrelid::regclass::text, 'name',c.conname,
        'definition',pg_get_constraintdef(c.oid)) order by c.conrelid::regclass::text,c.conname),'[]'::jsonb)
        from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='public'),
    'extensions', (select jsonb_agg(jsonb_build_object('name',extname,'version',extversion) order by extname) from pg_extension),
    'buckets', (select coalesce(jsonb_agg(jsonb_build_object(
        'id',b.id,'name',b.name,'public',b.public,
        'objects',(select count(*) from storage.objects o where o.bucket_id=b.id)) order by b.id),'[]'::jsonb)
        from storage.buckets b),
    'app_counts', jsonb_build_object(
        'workshops',(select count(*) from public.workshops),
        'profiles',(select count(*) from public.profiles),
        'products',(select count(*) from public.products),
        'customers',(select count(*) from public.customers),
        'sales',(select count(*) from public.sales),
        'sale_items',(select count(*) from public.sale_items),
        'inventory_movements',(select count(*) from public.inventory_movements),
        'contact_requests',(select count(*) from public.contact_requests),
        'order_events',(select count(*) from public.order_events),
        'order_reservations',(select count(*) from public.order_reservations))
) as backup_inventory;
commit;
