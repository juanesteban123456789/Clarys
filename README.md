# Clarys Android App

Base inicial del proyecto Android Studio creada en Java y Gradle a partir del prototipo HTML del proyecto Clarys.

## Qué incluye

- Proyecto Gradle con módulo `app`
- Actividades para login, catálogo, detalle, carrito, gestión e inventario
- Layouts XML con una línea visual similar a la maqueta HTML
- Logos del taller integrados desde `res/drawable`

## Cómo abrirlo

1. Abre Android Studio.
2. Selecciona `Open`.
3. Elige la carpeta `android-clarys-app`.
4. Espera la sincronización de Gradle.
5. Ejecuta la app en un emulador o dispositivo.

## Nota técnica

La ruta actual del proyecto contiene caracteres no ASCII en la carpeta padre. Por eso se agregó `android.overridePathCheck=true` en `gradle.properties`. Si más adelante quieres una configuración más limpia, puedes mover `android-clarys-app` a una ruta simple, por ejemplo `D:\Proyectos\ClarysAndroidApp`.
