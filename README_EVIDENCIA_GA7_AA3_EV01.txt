EVIDENCIA
GA7-220501096-AA3-EV01

PROYECTO
Clarys

TIPO DE PROYECTO
Aplicación móvil Android nativa.

PLATAFORMA / FRAMEWORK
Android SDK / Android nativo.

LENGUAJE
Java.

INTERFACES
XML.

IDE
Android Studio.

GESTIÓN DEL PROYECTO
Gradle.

LIBRERÍAS PRINCIPALES
AndroidX
Material Components
OkHttp
Glide
Google Play Services Auth

ALMACENAMIENTO DE DATOS
Supabase.

La aplicación utiliza Supabase para gestionar productos, inventario,
clientes, ventas, catálogo público, perfiles de administradores,
talleres y configuración.

ALMACENAMIENTO DE IMÁGENES
Supabase Storage.

La aplicación sube imágenes de productos al bucket product-images
y almacena la URL pública para mostrarla en catálogos y detalle.

VERSIONAMIENTO
Git + GitHub.

REPOSITORIO
https://github.com/juanesteban123456789/Clarys.git

RAMA DE DESARROLLO
desarrollo

ESTÁNDARES DE CODIFICACIÓN
- PascalCase para clases.
- camelCase para métodos y variables.
- MAYUSCULAS_CON_GUION_BAJO para constantes.
- paquetes en minúsculas.
- nombres descriptivos.
- JavaDoc en clases y métodos principales.

MÓDULOS CODIFICADOS
- Autenticación administrativa con correo y contraseña.
- Autenticación con Google.
- Acceso público para clientes.
- Gestión de productos.
- Control de inventario.
- Registro de ventas y pedidos.
- Catálogo administrativo y catálogo público.
- Gestión de clientes.
- Reportes y panel de control.
- Configuración del taller.
- Subida de imágenes de productos.

INTEGRACIÓN CON SUPABASE
La app usa OkHttp para consumir Supabase Auth, Supabase REST API,
funciones RPC y Supabase Storage. Las operaciones principales usan
métodos HTTP GET, POST y PATCH. El método DELETE está implementado
en el cliente HTTP, pero no se usa actualmente en los flujos principales.

ARCHIVO DE ESQUEMA DE BASE DE DATOS
supabase_schema_clarys.sql

ESTADO DE COMPILACIÓN
BUILD SUCCESSFUL

COMANDO DE COMPILACIÓN
.\gradlew.bat clean assembleDebug

OBSERVACIONES
El archivo local.properties no se incluye porque contiene configuración
local del SDK y valores propios de cada equipo. Android Studio puede
regenerarlo al abrir el proyecto.
