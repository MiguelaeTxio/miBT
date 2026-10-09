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

## HOJA DE RUTA PARA LA SIGUIENTE SESIÓN

1. Confirmar que el workflow de la última ejecución terminó en verde
   y que el APK está subido.
2. Miguel Ángel instala el APK, conecta cada dispositivo, pulsa
   *Compartir log* y envía el log al chat.
3. Analizar el log por dispositivo: qué porcentaje llega, por qué vía
   (HFP, GATT, oculta) y qué eventos de fabricante aparecen.
4. Decidir con datos si el H01 se cierra y se abre el H02.
