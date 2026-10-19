# Censos Arles — Android

Aplicación Android para registrar censos agrícolas de campo sin conexión. Código correspondiente a la versión 1.4.3 (`versionCode` 8).

## Funciones

- Censo de floración por lote, línea y árbol.
- Guardado local con Room, borradores y recuperación de avances.
- Captura como invitado y vinculación posterior a una cuenta.
- Sincronización con Supabase y recepción de avances de otros celulares del mismo censo.
- Observaciones, plantas muertas, creación de líneas y árboles, recorrido ascendente o descendente.
- Contador de árboles realizados y orden numérico de las líneas.
- Código de producción y frutos conservado; esos módulos están ocultos en la interfaz actual.

## Preparación

1. Abrir este directorio en Android Studio. Se requiere JDK 17 o superior para iniciar Gradle; el wrapper configura automáticamente el JDK 25 del daemon. También se necesita SDK de compilación 37 y Android 7.0 (API 24) o superior para ejecutar.
2. Copiar `local.properties.example` a `local.properties`, conservar el `sdk.dir` que genere Android Studio y configurar la URL y la clave **publicable** de Supabase. Nunca usar una clave secreta o `service_role` en la app.
3. Solicitar al administrador el catálogo de la plantación y copiar `catalogo.json` y `catalogo-version.txt` a `app/src/main/assets/` para uso interno. Las versiones de ambos archivos deben coincidir.
4. Configurar las cuentas y permisos del backend antes de iniciar sesión o sincronizar.

Este repositorio público incluye un catálogo vacío, sin lotes ni árboles reales. No contiene respaldos de celulares, conteos, contraseñas ni la configuración de producción. La compilación es posible con el catálogo vacío, pero no permite censar hasta cargar un catálogo válido. Las pruebas que verifican el catálogo de producción requieren esos archivos internos.

## Compilar y verificar

En PowerShell, usando JDK 17:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Para pruebas conectadas usar un emulador o dispositivo dedicado y la variante separada:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest -PverificationBuild=true --no-configuration-cache
```

Esa variante usa un paquete `.verification` para proteger la instalación de campo. Después generar el APK normal sin esa propiedad. Actualizar un celular de campo con `adb install -r`; no desinstalar ni borrar los datos para actualizar.

## Backend

El backend y el panel administrativo se gestionan por separado en [Censos-Arles](https://github.com/Almacen-del/Censos-Arles). La app utiliza Auth y las tablas `campo_members`, `campo_records`, `campo_sync_campaigns`, `campo_sync_censuses` y `campo_sync_catalogs`. El proyecto debe contar con sus migraciones y políticas de acceso.

Los tokens de sesión se cifran con Android Keystore. Room conserva los datos locales; los envíos usan identificadores estables y se marcan como sincronizados solo tras confirmación del servidor. Los registros compartidos conservan su autor y se protegen de sobrescrituras.

## Directorios

- `app/src/main/java/`: captura, persistencia, sincronización y pantallas.
- `app/src/main/res/`: iconos y recursos de Android.
- `app/src/main/assets/`: esquema vacío del catálogo.
- `app/src/test/`: pruebas unitarias.
- `app/src/androidTest/`: pruebas de Android.

La configuración y el catálogo usados por la app instalada no se modifican al preparar esta publicación.
