# Clarys 1.1.0

Implementacion para completar el ciclo de pedidos y preparar la demostracion.

## Cambios

- Inventario total, reservado y disponible; existencias por talla y color.
- Confirmacion reserva, cancelacion libera y entrega registra una sola venta.
- Precios/costos historicos y reintentos con clave de idempotencia.
- Historial de estados con fecha y actor, mas auditoria de cambios de stock.
- Carrito cifrado persistente y favoritos locales.
- Revision final del pedido; aviso al cambiar precios o disponibilidad.
- Busqueda con demora de 300 ms; filtros de categoria, talla/color, precio y disponibilidad.
- Orden por nombre, precio y productos recientes; relacionados, compartir y WhatsApp.
- Duplicacion de producto con stock inicial cero, categorias sugeridas y margen.
- Reportes por hoy, semana, mes o historial; grafico diario y exportacion CSV.
- Totales de 64 bits para evitar desbordamientos.
- Ayuda, privacidad, version, avisos y eliminacion de cuenta.

## Backend

Aplicar en orden 20260912_production_hardening.sql,
20261013_inventory_orders.sql y 20261014_order_statuses.sql.
La segunda migracion agrega triggers que
protegen tambien las escrituras REST anteriores; no simula reservas en Java.
Las variantes viven en products.variants y las reservas reales en
order_reservations. Las operaciones bloquean productos por ID en orden estable.

No modifica los pedidos historicos entregados ni genera ventas retroactivas.
Un pedido aprobado anterior a la migracion reserva al iniciar preparacion.
Los costos de ventas antiguas se reconstruyen del costo disponible; las ventas
nuevas guardan su costo exacto. Este limite historico se detalla en el guion.

Desplegar supabase/functions/delete-account/index.ts. Valida el JWT en Auth,
exige confirmacion y contrasena para cuentas de correo, limpia Storage y usa
la API administrativa para eliminar al usuario. Las dependencias comerciales
se eliminan con un hook especifico de borrado, probado en PostgreSQL aislado.
Los secretos del servidor no se incluyen en Android.

tools/Verify-ClarysBackend.ps1 comprueba compatibilidad y lectura anonima sin
crear datos. Tras ejecutar ACTIVACION_SUPABASE_CLARYS.sql, la comprobacion del
13 de octubre de 2026 devolvio PASS: backend v2, catalogo publico y restricciones
de lectura. El 14 de octubre de 2026 ambas funciones Edge respondieron OPTIONS
200 y POST sin JWT 401 con su mensaje de sesion requerida. La herramienta de
verificacion comprueba ahora tambien estos dos rechazos sin crear datos.
Despues se genero descripcion sobre pedidos de demostracion autorizados desde
el celular y se verifico el ciclo de reserva, entrega y cancelacion.
La prueba encontro un CHECK antiguo que rechazaba Contactado; la tercera
migracion lo amplia a los seis estados sin alterar filas. El verificador exige
ahora esa compatibilidad. No se eliminaron cuentas; los resultados e IDs de
demostracion estan en VALIDACION_DISPOSITIVO_20261014.txt.

## Avisos

JobScheduler comprueba pedidos en segundo plano cada 15 minutos como minimo,
sujeto a decisiones de Android, bateria, permisos y red. El panel abierto
consulta pedidos cada 30 segundos. No se presenta como push instantaneo.
Para push inmediato futuro se requiere un proveedor y configuracion adicional.

## Verificacion

Se incluyen pruebas Java, Android con preferencias aisladas, SQL transaccional,
dos conexiones simultaneas por la ultima unidad y pruebas de la Edge Function
con HTTP simulado. Ver GUIA_DEMOSTRACION_CLARYS.txt para comandos y guion.

Las APK de demostracion son Debug. No se configuro una firma Release porque
las propiedades de keystore no estan presentes. No se hizo commit ni push.
