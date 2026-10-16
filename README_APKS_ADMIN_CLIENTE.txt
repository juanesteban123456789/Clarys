CLARYS - APK ADMINISTRADOR Y APK CLIENTE

El proyecto utiliza sabores de producto de Gradle para generar dos APK desde
la misma base de código Java/XML.

1. APK ADMINISTRADOR

Variante: admin
Nombre visible: Admin
Identificador: com.clarys.app

Inicia en el acceso administrativo cuando no existe una sesión y en el panel
cuando la sesión está activa. Conserva el identificador original para mantener
la configuración existente de Google Sign-In.

Desde el panel, la opción Vista cliente abre el catálogo público. El botón
Panel permite regresar al menú administrativo sin cerrar la sesión.

Comando:
.\gradlew.bat assembleAdminDebug

Salida:
app/build/outputs/apk/admin/debug/app-admin-debug.apk

2. APK CLIENTE

Variante: cliente
Nombre visible: Catálogo
Identificador: com.clarys.app.catalogo

Inicia directamente en el catálogo público. No muestra acceso administrativo
ni la opción Más. Su menú contiene Catálogo, Carrito y Mis pedidos. Los
productos se organizan automáticamente en carruseles horizontales según las
categorías publicadas, con una vista Ver más en cuadrícula.

Comando:
.\gradlew.bat assembleClienteDebug

Salida:
app/build/outputs/apk/cliente/debug/app-cliente-debug.apk

Los identificadores diferentes permiten instalar ambos APK simultáneamente en
el mismo dispositivo. Ambos consumen el mismo proyecto público de Supabase,
pero solamente el APK administrador expone la navegación administrativa.
