EVIDENCIA
GA7-220501096-AA3-EV01

PROYECTO
Clarys

TIPO DE PROYECTO
Aplicacion movil Android nativa.

PLATAFORMA / FRAMEWORK
Android SDK / Android nativo.

LENGUAJE
Java.

INTERFACES
XML.

IDE
Android Studio.

GESTION DEL PROYECTO
Gradle.

LIBRERIAS PRINCIPALES
AndroidX
Material Components
OkHttp
Glide
Google Play Services Auth

ALMACENAMIENTO DE DATOS
Supabase.

La aplicacion utiliza Supabase para gestionar productos, inventario,
clientes, ventas, catalogo publico, perfiles de administradores,
talleres y configuracion.

ALMACENAMIENTO DE IMAGENES
Supabase Storage.

La aplicacion sube imagenes de productos al bucket product-images
y almacena la URL publica para mostrarla en catalogos y detalle.

VERSIONAMIENTO
Git + GitHub.

REPOSITORIO
https://github.com/juanesteban123456789/Clarys.git

RAMA DE DESARROLLO
desarrollo

ESTANDARES DE CODIFICACION
- PascalCase para clases.
- camelCase para metodos y variables.
- MAYUSCULAS_CON_GUION_BAJO para constantes.
- paquetes en minusculas.
- nombres descriptivos.
- JavaDoc en clases y metodos principales.

MODULOS CODIFICADOS
- Autenticacion administrativa con correo y contrasena.
- Autenticacion con Google.
- Acceso publico para clientes.
- Gestion de productos.
- Control de inventario.
- Registro de ventas y pedidos.
- Catalogo administrativo y catalogo publico.
- Gestion de clientes.
- Reportes y panel de control.
- Configuracion del taller.
- Subida de imagenes de productos.

INTEGRACION CON SUPABASE
La app usa OkHttp para consumir Supabase Auth, Supabase REST API,
funciones RPC y Supabase Storage. Las operaciones principales usan
metodos HTTP GET, POST y PATCH. El metodo DELETE esta implementado
en el cliente HTTP, pero no se usa actualmente en los flujos principales.

ARCHIVO DE ESQUEMA DE BASE DE DATOS
supabase_schema_clarys.sql

ESTADO DE COMPILACION
BUILD SUCCESSFUL

COMANDO DE COMPILACION
.\gradlew.bat clean assembleDebug

OBSERVACIONES
El archivo local.properties no se incluye porque contiene configuracion
local del SDK y valores propios de cada equipo. Android Studio puede
regenerarlo al abrir el proyecto.
