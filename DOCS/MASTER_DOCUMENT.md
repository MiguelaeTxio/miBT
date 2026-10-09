# Documento Maestro: Proyecto miBT

---
## 1. Visión General del Proyecto

App Android muy simple: un **visor en notificaciones de los
dispositivos Bluetooth a los que Miguel Ángel está conectado**. Por
cada dispositivo conectado, una notificación con:

- Nombre del dispositivo.
- En auriculares: batería del auricular **izquierdo** y del
  **derecho**.
- Batería restante del dispositivo y de su **estuche/bahía de carga**.
- Si cada elemento está **cargando o no**.

Dispositivos reales: unos auriculares Xiaomi (Buds 6, sin confirmar) y
varios dispositivos de AliExpress. Móviles: Redmi Note 10 y Redmi Note
9 Pro.

## 2. Arquitectura Técnica

- Android nativo, Kotlin, Gradle Kotlin DSL, sin dependencias salvo
  `androidx.core`. Interfaz construida por código (sin XML de layout).
- `BtMonitorService`: servicio en primer plano (tipo
  `connectedDevice`) que detecta los dispositivos conectados
  (perfiles HEADSET y A2DP) y publica **una notificación por
  dispositivo**.
- **Limitación conocida de Android:** la API pública no da la batería
  de dispositivos Bluetooth a apps de terceros. Vías posibles, por
  orden: (1) eventos de fabricante del perfil HFP
  (`ACTION_VENDOR_SPECIFIC_HEADSET_EVENT`, p. ej. `+IPHONEACCEV`,
  `+XEVENT`), que dan un único porcentaje; (2) servicio GATT estándar
  de batería (0x180F); (3) `BluetoothDevice.getBatteryLevel()` oculto,
  por reflexión; (4) protocolo propietario del fabricante por
  RFCOMM/BLE para izquierda/derecha/estuche/carga. El protocolo de los
  Xiaomi Buds 6 **no se conoce y no se inventa**: se obtiene de datos
  reales.
- **Estrategia (decidida S1):** la v1 es un build de **diagnóstico**
  que muestra lo que Android expone y **registra en un log todo lo que
  cada dispositivo anuncia** (eventos HFP, GATT, lectura oculta). Con
  ese log real se escribe después el módulo por fabricante para
  izquierda/derecha/estuche/carga.
- Log en `files/mibt-log.txt`, con botón de compartir en la app.
- Compilación solo en GitHub Actions (`.github/workflows/
  build-and-deploy.yml`): compila, sube el APK a PythonAnywhere y lo
  deja también como artefacto de la ejecución.

## 3. Hoja de Ruta Estratégica (hitos)

Estado de los hitos: ver `DOCS/ANNEX_ROUTER.md` (única fuente).

| Hito | Nombre | Anexo |
|---|---|---|
| 1 | Visor de diagnóstico y primeras notificaciones | `DOCS/ANNEX_H01.md` |
| 2 | Módulo por fabricante: izquierda/derecha/estuche/carga | (por crear, depende de los datos del H01) |

## 4. Directrices Técnicas Vinculantes

4.1. Leer el archivo real antes de modificarlo; nunca inferir
contenido de memoria.
4.2. Código en inglés; comentarios bilingües (EN, línea `---`, ES);
commits y logs en español.
4.3. Sin secretos en el repositorio ni en `DOCS/`: solo nombres de
secretos de GitHub Actions.
4.4. Sin comentario de cabecera con la ruta del propio archivo.
4.5. Cada bloque lógico cerrado se commitea y se empuja de inmediato.
4.6. Ningún dato de batería se inventa: si Android o el dispositivo no
lo exponen, la notificación dice "sin datos".
