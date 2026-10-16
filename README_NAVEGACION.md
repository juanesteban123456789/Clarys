# Navegacion de Clarys

## Regla de regreso

- Gestion: la flecha Atras, el boton Atras de Android y el icono Inicio regresan
  al panel. El regreso deja una sola pantalla raiz, sin productos o formularios
  debajo que puedan reaparecer al pulsar Atras.
- En el panel, Atras pide confirmar el cierre de sesion. Permanecer conserva la
  sesion; confirmar la cierra y deja el acceso administrativo como unica raiz.
- En la pantalla de acceso sin sesion, Atras permite confirmar la salida.
- Catalogo cliente: desde detalle, carrito, pedidos o ayuda se vuelve al catalogo
  principal. Desde una busqueda o filtros, Atras restablece primero las categorias.
  Desde el catalogo principal, Atras pide confirmar la salida de la aplicacion.
- El cliente no tiene sesion administrativa que cerrar. Salir no borra carrito,
  favoritos ni seguimiento local.
- La vista de catalogo abierta desde Gestion regresa al panel, sin cerrar sesion.
- Los formularios de producto, documento PDF y publicacion conservan sus avisos
  de cambios sin guardar. Se evita abandonar una venta o un pedido durante su envio.

Los dialogos nativos, selectores de archivos, teclado, Google e Instagram siguen
teniendo su propio comportamiento de Atras: este cambio controla las pantallas de
Clarys, no las aplicaciones externas.

## Implementacion

BaseScreenActivity centraliza OnBackPressedDispatcher, Inicio, confirmaciones y
limpieza de la pila con NEW_TASK + CLEAR_TASK. Los formularios usan el punto de
extension requestLeaveScreen para confirmar descarte o bloquear una operacion.
No se alteraron Supabase Auth, las tablas ni las operaciones de inventario.

La verificacion detecto tambien una SecurityException al abrir el panel en
Android 16: faltaba ACCESS_NETWORK_STATE para el JobScheduler de avisos. Se agrego
el permiso normal al manifest; no requiere un dialogo de autorizacion al usuario.

## Verificacion local del 16/10/2026

- Compilacion de ambas APK Debug: BUILD SUCCESSFUL.
- 52 pruebas unitarias por variante, sin fallos.
- Android Lint: sin errores; mantiene advertencias del proyecto.
- NavigationLocalTest ejecuta cuatro casos administrativos y tres de cliente.
  Los casos exclusivos de la otra variante se omiten intencionadamente.
- Comprueba raiz unica tras Inicio, Atras del sistema y del encabezado,
  confirmacion/cancelacion de salida, logout sin regreso a pantallas privadas,
  descarte de formulario y retorno desde la vista cliente de Gestion.
- Pruebas opt-in en emulador sin red con datos y sesion sinteticos.
- La ejecucion conjunta administrativa con PDF y publicaciones termino OK
  (11 casos, uno omitido por pertenecer al cliente). El catalogo termino OK
  (cinco casos, dos omitidos por pertenecer al administrador).
- No se hicieron pruebas, instalaciones ni cierres de sesion en el celular real.
- No se hizo commit, push ni despliegue de backend.

Antes del video, instalar las APK actualizadas y comprobar el recorrido personal.
