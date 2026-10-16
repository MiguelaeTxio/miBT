# ANEXO H03 — Mejoras de la v1 funcional: persistencia y otras

Abierto en S3 (2026-10-11) por PCH, a petición de Miguel Ángel, al
cerrar el H02 con la prueba real superada. Hito contenedor de las
mejoras posteriores a la v1 funcional, para tener trazabilidad y
documentación de cada una.

## Objetivo

Agrupar las mejoras de miBT tras el H02. Cada mejora entra en la hoja
de ruta solo cuando se concierta con Miguel Ángel.

## Contexto técnico

- La lista de batería por elemento de los Redmi Buds 6 Lite (`+XIAOMI`
  de 7 elementos, ver `DOCS/ANNEX_H02.md`) solo llega al conectar.
  Hasta S3 vive solo en memoria de `BtMonitorService` (`xiaomiBattery`,
  dirección → batería + hora), así que cualquier reinicio del servicio
  (actualización de la app, el sistema lo mata, reinicio del móvil) la
  pierde hasta la siguiente conexión: la notificación vuelve a "sin
  datos" aunque haya una lectura reciente.
- La posición de los auriculares no se persiste: llega en cada cambio
  y se borra al desconectar (decisión del H02).

## HOJA DE RUTA PARA LA SIGUIENTE SESIÓN

1. **Persistencia de la batería por elemento (concertado en S3).**
   Guardar en `SharedPreferences` la última `XiaomiBattery` de cada
   dispositivo con su hora de lectura cada vez que llega una lista
   `+XIAOMI` válida, y cargarla al crear el servicio. La notificación
   la sigue mostrando con su hora de lectura, de modo que se ve que es
   la de la última conexión. La posición no se persiste.
2. Commit + push, workflow en verde y Release publicada.
3. Prueba de Miguel Ángel: con los auriculares conectados y la lectura
   visible, actualizar la app (o forzar la detención) y comprobar que
   la notificación conserva izquierdo/derecho/estuche y la hora.
4. **Cifra genérica de batería con su origen (concertado en S3).** La
   línea "Batería: X%" no dice de qué elemento ni de qué fuente viene
   (en la prueba de S3 marcaba 100 % con el izquierdo al 63 %). Se
   etiqueta con su origen: HFP (`+IPHONEACCEV`, en tramos de 10 %, la
   única que se refresca durante el uso), GATT estándar o sistema
   (broadcast / `getBatteryLevel()` oculto). No se oculta con datos por
   elemento porque es la única cifra en vivo.
5. **Vista contraída con resumen por elemento (concertado en S3).** Con
   datos Xiaomi, el texto de la notificación plegada pasa a
   "I 63% ⚡ · D 100% ⚡ · E 100%" ("–" si no hay dato); sin ellos,
   queda la línea de batería actual.
6. **Posición en el título (concertado en S3).** Con posición conocida,
   el título es "{nombre} — {estado}": "ambos puestos", "izquierdo
   puesto", "derecho puesto" o "en el estuche"; si la posición es
   desconocida, solo el nombre.
7. Commit + push, workflow en verde, Release publicada y prueba de
   Miguel Ángel de los puntos 4 a 6.
8. **Estado en tiempo real por el protocolo propio de Xiaomi (decidido
   en S3).** Verificado con el log de S3: los auriculares no envían la
   trama de posición `+XIAOMI` por HFP en cada cambio (a las 09:5x se
   sacó el derecho y no llegó ninguna trama; el receptor seguía vivo,
   recibió `+IPHONEACCEV` a las 09:25:50), y la batería por elemento
   solo llega al conectar. Android tampoco recibe más que la cifra
   única (ajustes de Bluetooth: "Batería 80 %"). La app Xiaomi Earbuds
   sí muestra el estado actual, luego los auriculares lo exponen por
   un canal propio (anuncian, entre otros, los UUID
   `df21fe2c-2515-4fdb-8886-f12c4d67927c` y
   `8a482a08-5507-42ac-b673-a88df48b3fc7`). Pasos: (a) buscar
   documentación o código abierto del protocolo (p. ej. Gadgetbridge);
   (b) si no basta, capturar el tráfico app Xiaomi ↔ auriculares con el
   registro HCI de Bluetooth del modo desarrollador y descifrarlo con
   datos reales; (c) implementar la consulta/suscripción en miBT.

## DATOS REALES DE LA SONDA RFCOMM (S3, log de las 10:30–10:35)

- `0xFD2D` (canal de control de Gadgetbridge para Redmi Buds 5 Pro)
  **existe en los Buds 6 Lite** aunque no aparece en su lista de UUID.
  Con los auriculares ya conectados (10:30) rechazó la conexión; tras
  una reconexión (10:31:18) aceptó, y al reto de autenticación
  `FEDCBA C4 50 0012 00 01{16 bytes} EF` respondió con
  `FEDCBA 04 50 0013 00 00 01 266B677430D1ABFC6DBB153B52054115 EF`:
  exactamente la respuesta `AUTH_CHALLENGE` de Gadgetbridge (tipo 0x04
  RESPONSE, opcode 0x50, estado 0x00, seq 0x00). Protocolo confirmado.
  Menos de 1 s después los auriculares cerraron el socket: la sonda no
  continuó el apretón de manos (Gadgetbridge envía `AUTH_CONFIRM` 0x51
  y responde al reto de los auriculares con SAFER+).
- `df21fe2c-…` rechazó la conexión las dos veces.
- `8a482a08-…` aceptó (10:30:03) y emitió cada ~2 s una trama de 8 bytes
  `BE 50 {nn} 03 00 00 01 {cs}` con `nn` incremental y `cs` decreciente,
  sin enmarcado `FEDCBA`. No descifrada; no se usa.
- En esta prueba las tramas HFP de posición sí llegaron en cada cambio
  (10:35:10–10:35:21: `09`, `0C`, `06`, `03`), a diferencia de la de
  las 09:5x.

## RESULTADO DEL CANAL DE CONTROL (S3)

- Implementado en `xiaomi/XiaomiControlChannel.kt` + `xiaomi/XiaomiAuth.java`
  (commits `9a48255`, licencia/atribución `770382c`, enlace al código
  fuente en cada Release `7afe376`). La autenticación se verificó
  offline contra la respuesta real de los Buds 6 Lite antes de publicar.
- Prueba de Miguel Ángel (10:51): "funciona perfectamente en principio"
  — la notificación refleja batería y posición en tiempo real.
- **Corrección (log del 2026-10-12 09:07–09:08):** los dos TWS sueltos
  forman una sola pareja ante el móvil: al cambiar de auricular no
  apareció ningún dispositivo nuevo, todo llegó por `41:42:DE:1A:5B:D6`
  (suenan en estéreo cuando están los dos). La cifra `+IPHONEACCEV`
  pasó de 60 % a 100 % al entrar el otro auricular: es la del
  auricular que hace de principal, sin indicar cuál. Solo anuncian HFP,
  A2DP y AVRCP: no hay canal propio ni GATT del que sacar batería por
  auricular ni cuál está puesto. El alias en el título se retiró (no
  resolvía nada y no se pidió).

## SUBIDA DE LOGS A DRIVE (S3, 2026-10-16, pedida por Miguel Ángel)

- Botón "Subir logs a Drive" en la pantalla principal, emulando el de
  MiMoo (`com-actions-relay` §7): `drive/DriveLogUploader.kt` sube
  `mibt-log.txt` y el manifiesto `mibt_logs_manifest.txt` (el último) a
  "miBT - Intercambio Claude", sobrescribiendo en su sitio. Sin Retrofit:
  `HttpURLConnection` + `org.json` (PATCH vía `X-HTTP-Method-Override`).
- Requisito manual de Miguel Ángel (Google Cloud, proyecto
  `mimoo-drive`): crear un cliente OAuth tipo Android con paquete
  `com.miguelaetxio.mibt` y el SHA-1 que figura en las notas de cada
  Release de `miBTReleases`. Sin él, la autorización falla.
- Flujo desde ahora: el modelo pide "pulsa Subir logs a Drive y dime
  listo", lee primero el manifiesto y descarta la subida si su hora es
  anterior a la petición.
