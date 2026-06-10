# AlertBlo

Aplicación Android que recibe alertas desde un servidor remoto y las muestra como notificaciones, incluso con el móvil en silencio. Un servicio en segundo plano consulta el servidor cada 20 segundos y, si el dispositivo está dentro de la zona geográfica configurada, reproduce la alerta con sonido y vibración saltándose el modo No Molestar.

## Requisitos

- Android Studio Hedgehog o superior
- Android SDK 24+
- Servidor con los endpoints PHP (`get_alerta_json.php`, `create_alerta_silencio.php`)

## Configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/AlertBlo.git
cd AlertBlo
```

### 2. Crear `local.properties`

Copia el archivo de ejemplo y rellena tus valores:

```bash
cp local.properties.example local.properties
```

Edita `local.properties`:

```properties
sdk.dir=/ruta/a/tu/Android/Sdk          # En Windows: C:\Users\TuUsuario\AppData\Local\Android\Sdk
IP_SERVIDOR=http://tu-ip-o-dominio      # URL base del servidor, sin barra final
```

> `local.properties` está en `.gitignore` y nunca se sube al repositorio.

### 3. Sincronizar y compilar

1. Abre el proyecto en Android Studio.
2. **File → Sync Project with Gradle Files**.
3. **Build → Make Project** (Ctrl+F9).

Si Android Studio muestra *"Cannot resolve symbol BuildConfig"* antes del primer build, es normal — el archivo se genera durante la compilación.

## Permisos que solicita la app

| Permiso | Motivo |
|---|---|
| `POST_NOTIFICATIONS` | Mostrar notificaciones de alerta |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Determinar si el dispositivo está dentro de la zona |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Mantener el servicio de polling activo |
| `ACCESS_NOTIFICATION_POLICY` | Sonar aunque el móvil esté en No Molestar |
| `INTERNET` | Comunicación con el servidor |

La app también pedirá acceso a **No Molestar** (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) la primera vez que se inicie. Sin este permiso las alertas urgentes no suenan en silencio.

## Estructura del proyecto

```
app/src/main/java/com/example/alertblo/
├── MainActivity.java      # Pantalla principal, historial de alertas
├── AlertaService.java     # Servicio en segundo plano (polling cada 20 s)
├── Servidor.java          # Llamadas HTTP al servidor
├── GeoHandler.java        # Lógica de geolocalización
├── Alerta.java            # Modelo de datos
└── AlertaAdapter.java     # Adapter para el ListView
```

## Variables de entorno / secretos

| Variable | Archivo | Descripción |
|---|---|---|
| `IP_SERVIDOR` | `local.properties` | URL base del servidor PHP |
| `sdk.dir` | `local.properties` | Ruta local al Android SDK |

Ninguno de estos archivos se incluye en el repositorio. Consulta `local.properties.example` como referencia.
