# Documentos comerciales PDF de Clarys

## Alcance

Este modulo genera comprobantes comerciales y cotizaciones para el piloto.
**No emite facturas electronicas DIAN**, no calcula impuestos ni genera CUFE,
resoluciones de numeracion, firmas fiscales o validaciones tributarias.
Todos los PDF muestran: "No constituye factura electronica de venta."

No reemplaza Supabase Auth y no crea ventas, reservas ni movimientos de stock.
El estado inicial es "Pago no verificado". Marcar "Pago registrado por el taller"
es una declaracion manual del administrador, no una confirmacion bancaria.
Las cotizaciones nunca acreditan pago.

## Acceso

- Clarys Gestion: panel administrativo > Mas opciones > Documentos de venta.
- Desde el detalle de un pedido o de una venta, mediante su boton de documento.
- Solo usuarios administrativos autenticados; las pantallas no son exportadas.
- No se habilita este modulo en el flujo de Clarys Catalogo.

## Operacion

1. Crear un documento manual o seleccionar un pedido/venta del taller.
2. Completar cliente y, opcionalmente, identificacion, telefono, correo y notas.
3. En documentos manuales, agregar conceptos, cantidades, precios y descuento.
4. Elegir comprobante o cotizacion y guardar.
5. Revisar el PDF paginado, guardarlo, compartirlo o abrir la impresion Android.

Los documentos vinculados conservan cantidades y precios del origen. Si un pedido
ya tiene una venta asociada, se usan sus valores historicos y descuento.
No se admiten pedidos rechazados. Estas consultas siguen usando Supabase y sus
permisos existentes; no se hicieron cambios al backend para este modulo.

El guardado conserva una instantanea: los cambios posteriores del producto no
reescriben el documento. Repetir un documento del mismo tipo y origen abre el
existente. Para corregirlo, eliminar su copia local y volver a generarlo;
las copias ya exportadas deben gestionarse por separado.

## Guardado, privacidad y limites

- Historial privado de esta instalacion, separado por identificador del taller.
- Metadatos cifrados mediante Android Keystore; PDF en el almacenamiento privado
  de la app, sin cifrado adicional del archivo PDF.
- Compartir usa una copia temporal y un URI con permiso de lectura limitado.
  El FileProvider no expone directamente el historial privado.
- Guardar PDF abre el selector de documentos Android. Google Drive puede aparecer
  si esta instalado y disponible como proveedor; no hay sincronizacion automatica
  ni integracion directa con una cuenta Google Drive.
- Desinstalar la app o borrar sus datos elimina este historial local. Exportar los
  documentos importantes antes de hacerlo. No existe restauracion del historial
  desde un PDF exportado ni sincronizacion entre celulares.
- Borrar un documento local no elimina pedidos, ventas ni copias compartidas.
- Precios enteros, moneda configurada del taller, fecha de emision de Colombia.
- Limites: 100 conceptos, cantidad de 1 a 100000 y precio de 0 a 100000000.
- La numeracion interna CP/CT es identificativa, no una numeracion fiscal DIAN.

## Codigo principal

- `model/SalesDocument.java`: instantanea y validacion de totales.
- `data/SalesDocumentCodec.java`: serializacion JSON versionada.
- `data/SalesDocumentRepository.java`: historial privado, aislamiento e idempotencia.
- `util/SalesDocumentPdf.java`: PDF A4, logo optimizado, tablas y paginacion.
- `util/SalesDocumentPrintAdapter.java`: impresion y seleccion de paginas.
- `util/SalesDocumentFileProvider.java`: intercambio limitado por URI.
- `SalesDocumentsActivity.java`: listado, busqueda y seleccion de origen.
- `SalesDocumentFormActivity.java`: formulario y validaciones.
- `SalesDocumentPreviewActivity.java`: vista previa y exportacion.

Las rutas Java anteriores parten de `app/src/main/java/com/clarys/app/`.
Los layouts nuevos estan en `app/src/main/res/layout/`; textos y estilos en
`app/src/main/res/values/documents.xml`, con ajuste de autofill en `values-v26/`.

## Validacion local del 14/10/2026

Comando ejecutado:

```powershell
.\gradlew.bat assembleDebug assembleAdminDebugAndroidTest testAdminDebugUnitTest testClienteDebugUnitTest lintAdminDebug lintClienteDebug --console=plain
```

- Resultado: BUILD SUCCESSFUL.
- Unitarias: 45 por variante, sin fallos ni errores (90 ejecuciones).
- Lint: 0 errores y 638 advertencias por variante; no se ocultaron con baseline.
- Integracion: cuatro pruebas en emulador Android 16 local aislado, sin red.
  Comprueban aislamiento, cifrado de metadatos, idempotencia, fallo de escritura,
  paginacion de 65 conceptos, impresion de una pagina seleccionada, campos vacios,
  cantidad/descuento invalidos, guardado y exportacion con hash identico.
- Revision visual de vista previa y paginas generadas, incluidos acentos y ene.
- PDF de ejemplo de una pagina: aproximadamente 134 KB; ocho paginas: 154 KB.

Pruebas: `app/src/test/java/com/clarys/app/model/SalesDocumentTest.java` y
`app/src/androidTest/java/com/clarys/app/documents/SalesDocumentsLocalTest.java`.
La instrumentacion exige un emulador, modo avion y la opcion explicita
`clarysLocalPdfTest=true`; no se ejecuta de forma automatica en celulares reales.

No se probo una impresora fisica, una cuenta Drive ni la lectura de pedidos reales
en este ensayo. Los datos y la sesion usados por las pruebas son sinteticos y
locales. No se instalaron APK en el celular, no se modifico Supabase y no se hizo
commit ni push durante esta implementacion.
