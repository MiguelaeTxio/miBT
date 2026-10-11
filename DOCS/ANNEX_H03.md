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
