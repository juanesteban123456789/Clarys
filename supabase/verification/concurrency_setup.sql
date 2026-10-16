-- Solo para el contenedor PostgreSQL de pruebas.
insert into auth.users(id) values('10000000-0000-0000-0000-000000000009');
insert into public.workshops(id,owner_user_id,name,whatsapp)
values('20000000-0000-0000-0000-000000000009','10000000-0000-0000-0000-000000000009','Prueba concurrencia','+573000000009');
insert into public.profiles(user_id,workshop_id,role)
values('10000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000009','admin');
insert into public.products(id,workshop_id,name,sale_price,stock) overriding system value
values(910101,'20000000-0000-0000-0000-000000000009','Ultima unidad',1000,1);
insert into public.contact_requests(id,workshop_id,customer_name,customer_phone,items,status) overriding system value values
(910111,'20000000-0000-0000-0000-000000000009','Cliente A','573000011111','[{"product_id":910101,"product_name":"Ultima unidad","quantity":1,"unit_price":1000}]','contacted'),
(910112,'20000000-0000-0000-0000-000000000009','Cliente B','573000011112','[{"product_id":910101,"product_name":"Ultima unidad","quantity":1,"unit_price":1000}]','contacted');
