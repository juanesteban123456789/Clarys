do $$
begin
    if (select count(*) from public.contact_requests where id in(910111,910112) and status='approved')<>1 then
        raise exception 'FAIL: dos pedidos compitieron por la misma unidad';
    end if;
    if (select reserved_stock from public.products where id=910101)<>1 then
        raise exception 'FAIL: reserva incorrecta tras concurrencia';
    end if;
    raise notice 'PASS: dos transacciones simultaneas, una sola reserva';
end $$;
delete from auth.users where id='10000000-0000-0000-0000-000000000009';
