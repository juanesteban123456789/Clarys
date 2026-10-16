# Lista de chequeo: respaldo y restauracion de Clarys

Fecha: 14/10/2026. Responsable y aprobador: Juan Esteban Sepulveda Sepulveda.
Ejercicio: 20261014T211727Z-21e5cdab. Resultado: 16 Si y 1 No.

Aplica al NUEVO ensayo de recuperacion de datos y archivos, aprobado expresamente
con limitacion. No demuestra respaldos anteriores a las migraciones ya realizadas
ni una migracion completa de servicios Supabase. "Si" se interpreta junto con
la observacion, no como validacion de aspectos fuera del alcance.

| N. | Actividad | Cumple | Observacion |
| ---: | --- | :---: | --- |
| 1 | Version, fecha, origen y destino identificados | Si | Clarys 1.1.0; Supabase PostgreSQL 17.6 hacia PostgreSQL 17 local aislado y archivos locales; 14/10/2026. |
| 2 | Responsables identificados | Si | Juan Esteban Sepulveda Sepulveda: respaldo, ejecucion, validacion y aceptacion. |
| 3 | Motivo revisado | Si | Comprobar recuperabilidad de los datos y archivos, sin alterar produccion. |
| 4 | Respaldo completo de la base antes de iniciar | Si | Respaldo logico consistente de las 49 tablas antes de ESTE ensayo de restauracion; no antes de migraciones pasadas. |
| 5 | Objetos Storage respaldados aparte | Si | 8 archivos de 3 buckets en storage.zip.dpapi, separado del dump. |
| 6 | Integridad, fecha y ubicacion verificadas | Si | Manifiesto SHA-256 comprobado; fechas y resultados en evidence.json. |
| 7 | Respaldo seguro y separado del origen | Si | Fuera del proyecto y de Supabase; cifrado DPAPI y ACL restringida. Depende del mismo usuario/equipo, sin copia externa certificada. |
| 8 | Inventario de objetos | Si | Inventario de tablas/columnas, funciones publicas, RLS, restricciones, extensiones y buckets; fuentes de las dos Edge Functions de Clarys conservados. Configuracion/secretos remotos excluidos. |
| 9 | Estructuras y campos mapeados | Si | Restauracion sin transformacion; inventarios de tablas y columnas coincidentes. |
| 10 | Restauracion sin errores criticos | Si | Ejecucion final PASS; errores iniciales locales revisados y corregidos. |
| 11 | Archivos Storage migrados y verificados | Si | Restauracion LOCAL de 8 archivos, mapeo bucket/ruta y hashes identicos; no subida a otro servicio Storage. |
| 12 | Integridad de datos comparada | Si | Conteos y SHA-256 de contenido iguales en las 49 tablas; 8 archivos coincidentes. |
| 13 | Login y comunicacion de Clarys con el nuevo backend | No | La copia no tiene servicios Auth/REST/Storage ni una APK conectada. Las pruebas productivas previas no sustituyen esta comprobacion. |
| 14 | Catalogo, productos, inventario y pedidos probados | Si | Validacion a nivel SQL en la copia: integridad de datos, reglas de reservas, cancelacion, entrega, variantes e historial. No prueba de interfaz de la APK contra la copia. |
| 15 | Errores, logs y observaciones revisados | Si | Compatibilidad local corregida y pruebas finales satisfactorias; limitaciones documentadas antes de aprobar. |
| 16 | Objetivos confirmados | Si | Cumplido el alcance acordado: recuperar datos y archivos con integridad. No se certifica migracion completa de servicios/app. |
| 17 | Fecha y aprobador documentados | Si | Aprobacion expresa con limitacion: Juan Esteban Sepulveda Sepulveda, 14/10/2026 16:38:57 America/Bogota. |

## Evidencias y pendiente

Respaldo privado: C:\Users\user\.clarys-backup\respaldos\20261014T211727Z-21e5cdab.
Documentos del proyecto: ACTA_RESPALDO_MIGRACION_CLARYS.md y
RESPALDO_Y_MIGRACION_CLARYS.md. No publicar respaldos personales ni credenciales.

Para convertir el item 13 en Si: provisionar una replica independiente con
Auth/REST/Storage compatibles y probar login/consultas desde una APK exclusiva
de prueba. La aplicacion productiva no debe apuntar a esa replica.

Si el instructor exige migracion de plataforma completa o pruebas graficas de
todos los modulos contra el destino, los items 11, 14 y 16 no acreditan ese
alcance adicional y deben quedar pendientes hasta ejecutar esas pruebas.
