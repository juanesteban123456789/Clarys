-- Compatibilidad con proyectos que conservan el CHECK antiguo de cuatro estados.
-- No cambia filas, productos, usuarios, permisos ni el historial de pedidos.
begin;
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
