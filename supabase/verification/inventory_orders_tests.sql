-- Solo datos sinteticos, transaccion revertida al terminar.
begin;
set local request.jwt.claim.sub='10000000-0000-0000-0000-000000000001';
set local request.jwt.claim.role='authenticated';
insert into auth.users(id) values('10000000-0000-0000-0000-000000000001'),('10000000-0000-0000-0000-000000000002');
insert into public.workshops(id,owner_user_id,name,whatsapp) values
('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','Taller prueba A','+573000000001'),
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002','Taller prueba B','+573000000002');
insert into public.profiles(user_id,workshop_id,role) values
('10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','admin'),
('10000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002','admin');
insert into public.products(id,workshop_id,name,purchase_price,sale_price,stock,variants) overriding system value values
(910001,'20000000-0000-0000-0000-000000000001','Camiseta prueba',5000,10000,3,
'[{"id":"m-negro","size":"M","color":"Negro","stock":2},{"id":"l-azul","size":"L","color":"Azul","stock":1}]'),
(910002,'20000000-0000-0000-0000-000000000001','Producto general',1000,2000,5,'[]'),
(910003,'20000000-0000-0000-0000-000000000002','Producto privado B',999,1000,2,'[]');

do $$
declare v_order bigint; v_other bigint; v_token uuid; v_retry_token uuid; v_retry bigint;
    v_cancel bigint; v_count integer; v_sale bigint; v_error boolean;
begin
    select request_id,tracking_token into v_order,v_token from public.submit_catalog_request(
        'Cliente prueba','573000010001',
        '[{"product_id":910001,"variant_id":"m-negro","quantity":2,"unit_price":10000}]',
        '30000000-0000-0000-0000-000000000001');
    select request_id,tracking_token into v_retry,v_retry_token from public.submit_catalog_request(
        'Cliente prueba','573000010001',
        '[{"product_id":910001,"variant_id":"m-negro","quantity":2,"unit_price":10000}]',
        '30000000-0000-0000-0000-000000000001');
    if v_order<>v_retry or v_token<>v_retry_token then raise exception 'FAIL: reintento duplico el pedido'; end if;
    select request_id into v_other from public.submit_catalog_request('Segundo cliente','573000010002',
        '[{"product_id":910001,"variant_id":"m-negro","quantity":2,"unit_price":10000}]');
    update public.contact_requests set status='contacted' where id in(v_order,v_other);
    update public.contact_requests set status='approved' where id=v_order;
    if (select reserved_stock from public.products where id=910001)<>2 then raise exception 'FAIL: no reservo'; end if;
    if (select stock from public.products where id=910001)<>3 then raise exception 'FAIL: reserva descuento stock fisico'; end if;
    v_error:=false;
    begin update public.contact_requests set status='approved' where id=v_other;
    exception when others then v_error:=sqlerrm like '%Stock insuficiente%'; end;
    if not v_error then raise exception 'FAIL: sobreventa o error inesperado'; end if;
    v_error:=false;
    begin update public.products set variants='[]',stock=0 where id=910001;
    exception when others then v_error:=true; end;
    if not v_error then raise exception 'FAIL: permitio borrar stock reservado'; end if;
    v_error:=false;
    begin perform public.confirm_sale('Mostrador','573000010003','Efectivo',0,'Confirmada',
        '[{"product_id":910001,"variant_id":"m-negro","quantity":1}]');
    exception when others then v_error:=sqlerrm like '%Stock insuficiente%'; end;
    if not v_error then raise exception 'FAIL: venta manual consumo reserva'; end if;
    update public.contact_requests set status='approved' where id=v_order;
    if (select sum(quantity) from public.order_reservations where request_id=v_order)<>2 then
        raise exception 'FAIL: reserva duplicada'; end if;
    update public.contact_requests set status='preparing' where id=v_order;
    update public.contact_requests set status='completed' where id=v_order;
    update public.contact_requests set status='completed',admin_notes='Nota de prueba' where id=v_order;
    if (select count(*) from public.sales where request_id=v_order)<>1 then raise exception 'FAIL: venta duplicada'; end if;
    if (select stock from public.products where id=910001)<>1
        or (select reserved_stock from public.products where id=910001)<>0 then raise exception 'FAIL: stock de entrega'; end if;
    if (select total from public.sales where request_id=v_order)<>20000 then raise exception 'FAIL: total de venta'; end if;
    if (select count(*) from public.order_events where request_id=v_order)<>5 then raise exception 'FAIL: historial'; end if;
    v_error:=false;
    begin update public.contact_requests set status='rejected' where id=v_order;
    exception when others then v_error:=true; end;
    if not v_error then raise exception 'FAIL: reabrio pedido entregado'; end if;
    select request_id into v_cancel from public.submit_catalog_request('Cliente cancelacion','573000010004',
        '[{"product_id":910002,"quantity":4,"unit_price":2000}]');
    update public.contact_requests set status='contacted' where id=v_cancel;
    update public.contact_requests set status='approved' where id=v_cancel;
    update public.contact_requests set status='rejected' where id=v_cancel;
    if (select reserved_stock from public.products where id=910002)<>0
        or (select stock from public.products where id=910002)<>5 then raise exception 'FAIL: liberacion de reserva'; end if;
    v_error:=false;
    begin perform public.submit_catalog_request('Precio incorrecto','573000010005',
        '[{"product_id":910002,"quantity":1,"unit_price":1}]');
    exception when others then v_error:=sqlerrm like '%precio cambio%'; end;
    if not v_error then raise exception 'FAIL: acepto precio falso'; end if;
    v_error:=false;
    begin perform public.submit_catalog_request('Taller mezclado','573000010006',
        '[{"product_id":910002,"quantity":1,"unit_price":2000},{"product_id":910003,"quantity":1,"unit_price":1000}]');
    exception when others then v_error:=sqlerrm like '%mezclar talleres%'; end;
    if not v_error then raise exception 'FAIL: mezclo talleres'; end if;
    v_sale:=public.confirm_sale('Mostrador','573000010007','Efectivo',0,'Confirmada',
        '[{"product_id":910002,"quantity":1}]','30000000-0000-0000-0000-000000000002');
    if v_sale<>public.confirm_sale('Mostrador','573000010007','Efectivo',0,'Confirmada',
        '[{"product_id":910002,"quantity":1}]','30000000-0000-0000-0000-000000000002') then
        raise exception 'FAIL: venta manual duplicada'; end if;
    if (select stock from public.products where id=910002)<>4 then raise exception 'FAIL: descuento de venta manual'; end if;
    perform public.adjust_variant_stock(910001,'l-azul','Ajuste',0,'Conteo prueba');
    if (select stock from public.products where id=910001)<>0 then raise exception 'FAIL: ajuste a cero'; end if;
    raise notice 'PASS: reservas, cancelacion, entrega, idempotencia, variantes, precios, historial';
end $$;

-- El taller A no puede leer costos ni pedidos del taller B.
set local role authenticated;
do $$
begin
    if exists(select 1 from public.products where id=910003) then raise exception 'FAIL: aislamiento de talleres'; end if;
end $$;
reset role;
set local role anon;
do $$
declare v_error boolean:=false; v_catalog_count integer;
begin
    -- Consulta del catalogo como cliente anonimo, sin exponer costos privados.
    select count(id) into v_catalog_count from public.products
    where id=910002 and name='Producto general' and sale_price=2000 and stock=4;
    if v_catalog_count<>1 then raise exception 'FAIL: lectura del catalogo restaurado'; end if;
    begin perform purchase_price from public.products limit 1;
    exception when insufficient_privilege then v_error:=true; end;
    if not v_error then raise exception 'FAIL: costo publico'; end if;
    raise notice 'PASS: catalogo anonimo y proteccion de costos';
end $$;
reset role;
delete from auth.users where id='10000000-0000-0000-0000-000000000001';
do $$
begin
    if exists(select 1 from public.workshops where owner_user_id='10000000-0000-0000-0000-000000000001') then
        raise exception 'FAIL: no elimino taller del usuario';
    end if;
    raise notice 'PASS: eliminacion de cuenta con pedidos y ventas';
end $$;
rollback;
