-- Solo para PostgreSQL AISLADO de pruebas. Nunca ejecutar en Supabase.
create role anon;
create role authenticated;
create schema auth;
create schema storage;
create table auth.users(id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid;
$$;
create function auth.role() returns text language sql stable as $$
    select coalesce(nullif(current_setting('request.jwt.claim.role',true),''),'authenticated');
$$;
create table storage.buckets(id text primary key,name text,public boolean);
create table storage.objects(id uuid primary key,bucket_id text,name text);
alter table storage.objects enable row level security;
\i /workspace/supabase_schema_clarys.sql
grant usage on schema public,auth to anon,authenticated;
grant all on all tables in schema public to anon,authenticated;
grant usage,select on all sequences in schema public to anon,authenticated;
\i /workspace/supabase/migrations/20260912_production_hardening.sql
\i /workspace/supabase/migrations/20261013_inventory_orders.sql
-- Reproduce la restriccion legacy que solo se encontro en el proyecto remoto.
alter table public.contact_requests add constraint contact_requests_status_check
    check(status in ('pending','approved','completed','rejected'));
\i /workspace/supabase/migrations/20261014_order_statuses.sql
