# Clarys: preparación para producción

Este documento describe los pasos que deben completarse antes de distribuir
las aplicaciones **Admin** y **Catálogo** a usuarios reales.

## Orden de publicación

1. Crear una copia de seguridad de la base de datos de Supabase.
2. Ejecutar y verificar `supabase/migrations/20260912_production_hardening.sql`
   primero en un proyecto de prueba y después en producción.
   Después ejecutar `supabase/verification/production_hardening_checks.sql`;
   este segundo archivo es únicamente de lectura.
3. Volver a desplegar la Edge Function `generate-order-description`.
4. Configurar las credenciales de firma sin guardarlas en Git.
5. Registrar en Google OAuth los certificados SHA-1 y SHA-256 de release para
   los paquetes `com.clarys.app` y `com.clarys.app.catalogo`.
6. Incrementar `versionCode` y ajustar `versionName` antes de cada publicación.
7. Generar y probar las variantes release firmadas.
8. Publicar primero el backend y después las aplicaciones móviles compatibles.

## Firma Android

Gradle lee estos valores desde variables de entorno, `local.properties` o
propiedades Gradle locales:

```text
CLARYS_KEYSTORE_PATH
CLARYS_KEYSTORE_PASSWORD
CLARYS_KEY_ALIAS
CLARYS_KEY_PASSWORD
```

Los archivos `*.jks`, `*.keystore`, `keystore.properties` y
`local.properties` están excluidos de Git. La llave y sus contraseñas deben
conservarse en un gestor de secretos y en una copia de seguridad separada.

Antes de empaquetar, ejecutar:

```powershell
.\gradlew.bat verifyProductionConfiguration
```

## Supabase

La migración de producción realiza, entre otras, estas protecciones:

- oculta costos de compra y datos internos al rol anónimo;
- restringe imágenes y comprobantes a la carpeta del taller propietario;
- crea talleres y perfiles mediante una sola operación transaccional;
- valida productos, precios, cantidades y taller dentro del servidor;
- reemplaza la consulta pública por teléfono por códigos aleatorios;
- evita salidas de inventario superiores al stock existente;
- limita los RPC administrativos al rol autenticado.

La clave publicable de Supabase puede estar dentro de la aplicación cliente.
Nunca se debe incluir `service_role`, secretos OAuth ni secretos SMTP.

## Configuración externa pendiente

- Confirmar RLS y permisos ejecutando pruebas con dos talleres distintos.
- Activar protección antiabuso para solicitudes públicas. La migración limita
  envíos repetidos por teléfono, pero para exposición abierta se recomienda
  añadir CAPTCHA o una Edge Function con límites por origen.
- Verificar SMTP, confirmación y recuperación de correo.
- Configurar URLs autorizadas y Google OAuth para las firmas de producción.
- Migrar el Google Sign-In heredado a Android Credential Manager antes de que
  Google retire definitivamente el SDK anterior.
- Definir política de privacidad y tratamiento de nombre/teléfono del cliente.
- Configurar copias de seguridad, alertas y revisión de logs en Supabase.

## Validación mínima antes de publicar

- Registro por correo y por Google.
- Inicio, renovación, revocación y cierre de sesión.
- Aislamiento de productos, pedidos, clientes e inventario entre dos talleres.
- Catálogo público sin `purchase_price`, `owner_user_id` ni notas internas.
- Creación de pedidos, seguimiento desde el mismo dispositivo y rechazo de
  consultas por número telefónico.
- Concurrencia de stock: dos solicitudes sobre la última unidad.
- Subida, lectura y eliminación de imágenes por el taller propietario.
- Errores con red lenta, modo avión, timeout y respuestas 4xx/5xx.
- Instalación y actualización de ambas APK/AAB release firmadas.

No debe anunciarse la versión como lista para producción hasta completar la
migración, la firma y estas pruebas sobre un entorno equivalente al real.
