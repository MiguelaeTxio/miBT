# ANEXO H01 — Visor de diagnóstico y primeras notificaciones

**Estado:** EN PROGRESO

## Objetivo

Tener en el móvil una app que (a) lista los dispositivos Bluetooth
conectados con una notificación por dispositivo, (b) muestra la
batería cuando Android la expone, y (c) registra en un log todo lo que
cada dispositivo anuncia, para escribir con datos reales el módulo por
fabricante (H02).

## COMPLETADAS EN S1

- Esqueleto del proyecto, `DOCS/` y workflow de compilación.
- Servicio en primer plano con una notificación por dispositivo.
- Log de diagnóstico con botón de compartir.

## DATOS REALES RECOGIDOS (S1)

Dispositivo "TWS" (41:42:DE:1A:5B:D6, auriculares AliExpress),
Redmi Note 10 (M2101K6G), Android 13:

- Anuncia el perfil HFP con extensión Apple: `+XAPL`
  (`000D-0001-0101`, características `2` = informa de batería) y
  `+IPHONEACCEV` con args `[1,1,9]` = clave 1 (batería), valor 9 =
  **100 %**. Llega también por el broadcast oculto
  `BATTERY_LEVEL_CHANGED` (nivel=100) y por `getBatteryLevel()`
  oculto (100).
- Solo un porcentaje único. **No** hay izquierdo/derecho/estuche ni
  estado de carga por HFP en este dispositivo.
- La sonda GATT no dejó ninguna línea en el log (sin servicios ni
  errores): pendiente de revisar.
- Pendiente de registrar: Xiaomi Buds 6 y el resto de dispositivos.

## HOJA DE RUTA PARA LA SIGUIENTE SESIÓN

1. Confirmar que el workflow de la última ejecución terminó en verde
   y que el APK está subido.
2. Miguel Ángel instala el APK, conecta cada dispositivo, pulsa
   *Compartir log* y envía el log al chat.
3. Analizar el log por dispositivo: qué porcentaje llega, por qué vía
   (HFP, GATT, oculta) y qué eventos de fabricante aparecen.
4. Decidir con datos si el H01 se cierra y se abre el H02.
