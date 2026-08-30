-- Clarys - descripción automática de pedidos
-- Ejecutar una sola vez en Supabase SQL Editor antes de usar la IA.

alter table public.contact_requests
    add column if not exists ai_description text;

comment on column public.contact_requests.ai_description is
    'Resumen administrativo del pedido generado automáticamente.';
