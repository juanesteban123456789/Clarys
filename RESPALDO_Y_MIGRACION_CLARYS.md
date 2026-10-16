# Respaldo y restauracion de Clarys

Fecha de preparacion: 14/10/2026. App: Clarys 1.1.0, Java/XML.
Responsable de respaldo y ejecucion: Juan Esteban Sepulveda Sepulveda.
Responsable de validacion y aceptacion: Juan Esteban Sepulveda Sepulveda.
Aceptacion final: APROBADO CON LIMITACION por el responsable el 14/10/2026,
16:38:57 America/Bogota (21:38:57 UTC). Confirmacion: "Apruebo con esa limitacion".

## Alcance y cronologia

Este es un NUEVO ejercicio de respaldo y restauracion verificable.
No pretende demostrar un respaldo previo a las migraciones ya ejecutadas.
Origen: proyecto Supabase mzqvmddxxlzjfruksgnz con el esquema actual.
Destino utilizado: PostgreSQL 17 aislado en Docker, base clarys_restored,
sin red y sin puertos publicados; archivos restaurados en una carpeta privada.
El contenedor de ensayo se elimina al terminar; se conserva el respaldo cifrado.
No se cambio el backend de la app productiva para realizar el ensayo.

Objetivo aceptado: conservar y recuperar datos logicos y archivos; contrastar
contenido e integridad; documentar resultados y aceptacion. No certifica una
migracion completa de servicios Supabase ni una APK conectada a la replica.
Un ZIP de codigo o el esquema SQL no sustituyen un respaldo de datos.

## Preparacion segura

Ejecutar desde la raiz del proyecto en Windows PowerShell 5.1 o PowerShell 7:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Configure-ClarysBackup.ps1
```

Obtener host y usuario desde Supabase > Connect > Session pooler (puerto 5432).
Usar la contrasena de PostgreSQL, no la contrasena del usuario de Clarys.
Obtener la secret key administrativa de Settings > API Keys; alternativamente
usar la legacy service_role. No introducir la publishable/anon.
No restablecer contrasenas existentes sin conocer que integraciones dependen de ellas.

Los secretos se capturan sin eco, se cifran con Windows DPAPI y se guardan en
%USERPROFILE%\.clarys-backup\clarys-backup.config.json, fuera del repositorio.
La carpeta limita acceso al usuario actual y SYSTEM. DPAPI depende del usuario
y del equipo; esta configuracion no es un respaldo portable de las credenciales.
No se incorporan secretos a Java, XML, Gradle, local.properties ni las APK.
La clave administrativa tiene permisos amplios aunque el respaldo del origen
solo debe usar operaciones de lectura.

Comprobar disponibilidad local sin revelar secretos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Configure-ClarysBackup.ps1 -Check
```

Esto NO prueba la conexion ni crea el respaldo. El desarrollador debe continuar
el procedimiento despues de esta configuracion, no marcar los pendientes como cumplidos.
ExecutionPolicy Bypass se limita al proceso iniciado; no cambia la politica permanente.

### Verificacion real del acceso

Docker Desktop debe estar iniciado. Descargar el certificado CA desde Supabase
Database > Settings > SSL Configuration. Es publico; no es una clave privada.
Guardar prod-ca-2021.crt en Descargas o indicar su ruta mediante -CertificatePath.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Verify-ClarysBackupAccess.ps1
```

Esta comprobacion consulta buckets y hace SELECT en public, auth y storage.
Exige TLS verify-full con el certificado del panel. No guarda filas personales,
no crea respaldos y no cambia datos. El archivo temporal de acceso PostgreSQL
esta fuera del proyecto, hereda su ACL restringida y se elimina al terminar.
La contrasena no se incluye en los argumentos de Docker o PostgreSQL.

El 14/10/2026 pasaron DPAPI, acceso administrativo de lectura a tres buckets y
conexion PostgreSQL 17.6 con TLS verify-full. Se utilizo el certificado publico
C:\Users\user\Downloads\prod-ca-2021.crt. No se desactivo la validacion TLS.

## Inventario y mapeo

Ejecutar supabase/verification/backup_inventory.sql para registrar objetos reales
del origen: tablas y columnas, funciones, RLS, restricciones, extensiones, buckets
y conteos de las tablas de negocio. No devuelve filas personales ni claves.

El espejo restaurado debe preservar sin transformacion los nombres, tipos,
IDs, UUID, claves foraneas y secuencias del inventario capturado. Comparar
contenidos mediante hashes estables ademas de conteos; un conteo igual no
demuestra por si solo que los datos sean iguales.

Recursos conocidos de Clarys para contrastar con el inventario real:

| Recurso origen | Destino de restauracion | Regla |
| --- | --- | --- |
| public.workshops y profiles | Mismas tablas | Preservar propietario, taller y rol |
| public.products | Misma tabla | Preservar stock, reserved_stock, variantes, costos e imagen |
| public.customers | Misma tabla | Preservar IDs y relaciones; proteger datos personales |
| public.sales y sale_items | Mismas tablas | Preservar totales y precios/costos historicos |
| public.inventory_movements | Misma tabla | Preservar trazabilidad |
| public.contact_requests | Misma tabla | Preservar estados y tokens de seguimiento, sin publicarlos |
| public.order_events y order_reservations | Mismas tablas | Preservar historial y reservas |
| auth | Esquema Auth compatible | Preservar identidades; no divulgar tokens ni hashes |
| storage.buckets y objects | Metadatos compatibles | Los archivos binarios requieren respaldo separado |
| product-images | Bucket equivalente | Conservar rutas y comprobar SHA-256 de cada archivo |
| order-receipts | Bucket privado equivalente | Conservar privacidad y comprobar SHA-256 |
| public-assets | Bucket equivalente | Conservar el logo y demas recursos que existan |
| delete-account | Funcion Edge del entorno de prueba | Conservar codigo; secretos por canal separado |
| generate-order-description | Funcion Edge del entorno de prueba | Conservar index.ts y description.ts |
| RLS, funciones, extensiones y configuracion | Equivalentes compatibles | Comparar con inventario, no asumir equivalencia |

El inventario real y los hashes incluyen las 49 tablas permanentes encontradas,
tambien los esquemas Auth y Storage. Se compararon columnas, funciones publicas,
RLS, restricciones y extensiones. Se conservaron los fuentes de las funciones
Edge; no se exportaron sus secretos ni la configuracion remota completa.

## Resultado ejecutado

Ejercicio: 20261014T211727Z-21e5cdab.
Captura: 14/10/2026, 16:17:27 a 16:18:22 America/Bogota.
Primera restauracion satisfactoria: 16:32:16 America/Bogota.

Ubicacion privada:
C:\Users\user\.clarys-backup\respaldos\20261014T211727Z-21e5cdab

| Archivo | Bytes | Contenido |
| --- | ---: | --- |
| database.dump.dpapi | 484214 | Respaldo logico consistente de PostgreSQL |
| storage.zip.dpapi | 1015670 | 8 archivos, hashes y mapeo bucket/ruta |
| metadata.zip.dpapi | 38118 | Inventarios, fuentes Edge y registros del ensayo |
| manifest.json | Ver archivo | SHA-256 y tamanos de los tres archivos cifrados |
| evidence.json | Ver archivo | Resultados, fechas y aceptacion expresa |

Se recuperaron las 49 tablas con conteos y hashes de contenido identicos. Los
8 archivos de product-images, public-assets y order-receipts coinciden en
tamano y SHA-256. Se extrajeron localmente con su mapeo original; no se
subieron a un segundo servicio Storage ni se cambiaron los objetos del origen.

Las pruebas SQL en la copia pasaron: catalogo anonimo, reservas, cancelacion, entrega,
idempotencia, variantes e historial. Usaron datos sinteticos revertidos.
La copia no dispone de Auth/REST/Storage API: no se probo login ni consultas
desde una APK conectada a ella. Las pruebas anteriores del dispositivo contra
produccion no sustituyen esa validacion. Lista final: 16 Si y 1 No (item 13),
con observaciones que limitan los items 11, 14 y 16 al alcance realmente probado.

Los errores iniciales de compatibilidad de la restauracion aislada se revisaron
y corrigieron: esquema public, privilegios del event trigger y search_path.
La ejecucion final termino satisfactoriamente, sin escrituras en el origen.
Los temporales sin cifrar se eliminaron tras comprobar el manifiesto.

## Repetir la verificacion del respaldo cerrado

Requiere este usuario/equipo Windows, Docker Desktop, la imagen PostgreSQL
indicada por el script y los SQL de verificacion del proyecto. No necesita
conectarse a produccion ni volver a usar la clave administrativa.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Restore-ClarysBackupTest.ps1 -RunPath "C:\Users\user\.clarys-backup\respaldos\20261014T211727Z-21e5cdab"
```

El script verifica el manifiesto, descifra en una carpeta privada temporal,
restaura sin red, compara contenido y limpia el contenedor y los temporales.
No incluir respaldos personales ni la configuracion de acceso en Git/SENA.

## Limitaciones y siguiente validacion

DPAPI CurrentUser liga los archivos al usuario/equipo; no se certifica
recuperacion desde otro PC ni proteccion frente a perdida de este equipo.
El respaldo es logico: no incluye binarios del motor ni toda la plataforma.
Secretos/configuracion OAuth, SMTP, JWT y contrasenas de roles requieren gestion
separada. Vault no contenia secretos en la captura. Los fuentes Edge conservados
no prueban por si solos la equivalencia de despliegues remotos.

Para cerrar el item 13 se necesita un entorno independiente con servicios
Auth/REST/Storage compatibles y una APK de prueba configurada exclusivamente
para esa copia. No cambiar la app productiva ni reutilizar sus sesiones.

La captura de la base y la de Storage no son una transaccion unica: impedir
cambios durante la captura o detectar y registrar diferencias/reintentar. No
llamar completo a un respaldo que omita archivos, esquemas o configuracion necesaria.

## Referencias

- https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore
- https://supabase.com/docs/guides/local-development/restoring-downloaded-backup
- VALIDACION_DISPOSITIVO_20261014.txt: pruebas anteriores de la app, no prueba de restauracion.
