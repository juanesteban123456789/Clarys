# Preparacion de publicaciones para Instagram

## Acceso y alcance

Clarys Gestion > Gestionar productos > Detalle > Preparar publicacion.
Tambien se accede desde el detalle del catalogo administrativo del propio taller.
Solo administradores autenticados pueden preparar piezas de sus productos activos.
Se requiere una foto; nunca se modifica el producto, su stock ni Supabase.

Esta primera version prepara UNA pieza por producto. No tiene carruseles,
programacion, publicidad pagada ni publicacion automatica.

## Flujo con una cuenta personal

1. Elegir Publicacion (1080x1350) o Historia (1080x1920).
2. Revisar la foto completa, logo de Clarys y nombre del taller.
3. Elegir si la imagen debe mostrar precio y WhatsApp.
4. Revisar/editar el texto generado con nombre, precio, tallas, colores y contacto.
5. Copiar texto y compartir la imagen mediante el selector Android.
6. Elegir Instagram, pegar el texto si hace falta y confirmar alli la publicacion.

Si Instagram no aparece o no acepta el destino deseado, guardar la imagen y
seleccionarla desde Instagram. Guardar usa el selector Android; la ubicacion
elegida puede no aparecer automaticamente en la galeria del dispositivo.

No se pide una cuenta profesional, OAuth de Meta, contrasena de Instagram ni
token de publicacion. No se utiliza la API de Instagram. El formato Historia
solo prepara una imagen vertical, no invoca la API Sharing to Stories.

El selector envia imagen y texto, pero cada app receptora decide que admite.
Por eso existe un boton independiente para copiar la descripcion.
No se publican imagenes, comentarios ni mensajes sin la accion del usuario.

## Privacidad y comportamiento

- Se incluyen solo datos publicos del producto y contacto comercial del taller;
  nunca costos de compra, tokens ni informacion de clientes.
- La foto puede descargarse desde su URL existente; luego se procesa localmente.
- Los JPEG son temporales en cache y se comparten con permiso de lectura por URI.
- Las opciones actualizan tambien la descripcion generada mientras no haya
  ediciones manuales. Un texto editado se conserva; revisarlo o restablecerlo si
  se desea aplicar de nuevo la plantilla con las opciones actuales.
- Los borradores no se conservan al salir; se pide confirmacion si se edito texto.
- No hay enlace publico al catalogo porque aun no se ha configurado uno. La pieza
  dirige al WhatsApp comercial cuando esta disponible, sin inventar direcciones.
- El estado de compartido no se presenta como una publicacion confirmada.

## Comprobantes de pedidos

Este cambio NO automatiza los documentos PDF. En el detalle de un pedido,
el boton de documento abre el formulario con cliente y conceptos precargados.
El administrador revisa y guarda el comprobante o cotizacion. No se crea un PDF
automaticamente al recibir pedidos, y estos documentos NO son facturas DIAN.

## Validacion local

Validado el 16/10/2026: assembleDebug, 52 pruebas unitarias por variante y Lint
sin errores. Dos pruebas adicionales en emulador aislado y sin red comprobaron
los dos formatos, JPEG compartible, permiso de solo lectura, texto personalizado,
opciones y exportacion de una historia. Se revisaron imagenes y captura de pantalla.
La ejecucion conjunta final con las cuatro pruebas de comprobantes tambien paso:
OK (6 tests). Se ajusto el cierre de la Activity de prueba antes de limpiar su
sesion simulada, para aislar correctamente las pantallas entre casos.
Las pruebas usan datos sinteticos, no una cuenta real de Instagram.

No se probo publicar en Instagram ni se instalo esta actualizacion en el celular.
No se hizo commit, push ni despliegue en Supabase.

## Archivos

- app/src/main/java/com/clarys/app/ProductPromotionActivity.java
- app/src/main/java/com/clarys/app/model/ProductPromotion.java
- app/src/main/java/com/clarys/app/util/ProductPromotionImage.java
- app/src/main/res/layout/activity_product_promotion.xml
- app/src/main/res/values/promotions.xml
- app/src/test/java/com/clarys/app/model/ProductPromotionTest.java
- app/src/androidTest/java/com/clarys/app/promotions/ProductPromotionLocalTest.java

Se reutilizan el FileProvider de fotos y ProductSharing, y se agrega una entrada
no exportada en AndroidManifest.xml y un boton privado en el detalle del producto.

Referencia: https://developer.android.com/training/sharing/send
