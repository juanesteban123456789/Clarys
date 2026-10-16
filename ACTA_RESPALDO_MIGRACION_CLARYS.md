# Acta de respaldo, restauracion y aceptacion

Estado: EJECUTADO Y APROBADO CON LIMITACION. Aprobacion expresa en el chat;
no se presenta como firma digital ni certificacion de una migracion integral.
Fecha de preparacion: 14/10/2026.
Proyecto: Clarys 1.1.0.
Responsable de respaldo, migracion y validacion: Juan Esteban Sepulveda Sepulveda.
Persona que acepta el resultado: Juan Esteban Sepulveda Sepulveda.

## Identificacion del ejercicio

- Origen: Supabase mzqvmddxxlzjfruksgnz, esquema vigente al capturar el respaldo.
- Destino: PostgreSQL 17 aislado en Docker, base clarys_restored, sin red ni
  puertos publicados; archivos extraidos en carpeta local privada.
- Motivo: verificar que los datos y archivos de Clarys puedan recuperarse.
- Inicio del respaldo: 14/10/2026 16:17:27 America/Bogota (21:17:27 UTC).
- Fin del respaldo: 14/10/2026 16:18:22 America/Bogota (21:18:22 UTC).
- Restauracion satisfactoria: 14/10/2026 16:32:16 America/Bogota.
- Ubicacion: C:\Users\user\.clarys-backup\respaldos\20261014T211727Z-21e5cdab.
- Manifiesto SHA-256: manifest.json verificado contra tres archivos cifrados.

## Resultados verificados

- Respaldo logico consistente: PASS; 49 tablas permanentes, incluidos Auth y Storage.
- Archivos Storage separados: PASS; 8 objetos de 3 buckets.
- Inventario/mapeo: PASS; tablas, columnas, funciones publicas, RLS,
  restricciones, extensiones y metadatos de buckets.
- Restauracion aislada: PASS; todas las tablas coinciden en conteo y SHA-256.
- Archivos extraidos: PASS; tamanos y hashes identicos, con mapeo bucket/ruta.
- Reglas de negocio SQL: PASS; reservas, cancelacion, entrega, idempotencia,
  variantes e historial; las pruebas sinteticas se revirtieron.
- APK conectada a Auth/REST/Storage de la copia: NO EJECUTADO. Esos servicios
  no se provisionaron en este ensayo de base de datos y archivos.
- Errores iniciales revisados y corregidos; ejecucion final sin errores criticos.
- Fuente productiva: solo lectura; no se cambio el backend de Clarys.
- Temporales sin cifrar eliminados; archivos finales cifrados DPAPI, ACL privada.

Las pruebas productivas documentadas en VALIDACION_DISPOSITIVO_20261014.txt
no sustituyen pruebas realizadas contra una copia restaurada.
El nuevo respaldo no demuestra que existiera uno antes de las migraciones anteriores.

## Aceptacion

Decision: APROBADO CON LIMITACION para el ensayo de recuperacion de datos/archivos.
Fecha y hora de aceptacion: 14/10/2026 16:38:57 America/Bogota
(2026-10-14T21:38:57.6392724Z).
Nombre: Juan Esteban Sepulveda Sepulveda.
Confirmacion expresa: "Apruebo con esa limitacion", recibida del responsable.
Registro tecnico: evidence.json, acceptedBy y acceptedAtUtc.
Observaciones: 16 items Si y 1 No; item 13 pendiente. Los items 11, 14 y 16
se aceptan dentro del alcance local de archivos y pruebas SQL, no como una
migracion integral de la app. No se exportaron secretos de plataforma.
DPAPI requiere el mismo usuario Windows/equipo; no es un respaldo portable.

El contenedor y los archivos temporales del ensayo se eliminaron; el respaldo
cifrado se conserva para repetir la verificacion. No se certifica restauracion
de servicios completos, inicio de sesion en la replica ni recuperacion en otro PC.
