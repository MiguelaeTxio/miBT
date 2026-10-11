# RESUMPTION_POINT — miBT

**Hito EN PROGRESO:** Hito 02 — Módulo Xiaomi: izquierdo/derecho/
estuche/carga y posición (`DOCS/ANNEX_H02.md`).

## Próximos pasos concretos

Ver "HOJA DE RUTA PARA LA SIGUIENTE SESIÓN" en `DOCS/ANNEX_H02.md`:
parser `XiaomiProtocol.kt`, recepción de `+XIAOMI` en
`BtMonitorService.kt`, notificación con los datos por elemento y la
posición, build en verde y prueba en el móvil. El protocolo verificado
está en la sección "Contexto técnico" del mismo anexo.

## Cómo arrancar la próxima sesión de miBT

- Arranque normal con `newflow-android-pisa`; chat nuevo: enlazar
  `miBT` con `add_repo` (access push), sin PAT.
- Secretos de GitHub Actions ya creados: `DEBUG_KEYSTORE_BASE64`,
  `PA_API_TOKEN`, `PA_USERNAME`, `RELEASES_REPO_TOKEN`. Repositorio de
  Releases `miBTReleases` creado.
- El repositorio responde con aviso "This repository moved" hacia
  `MiguelaeTxio/miBT.git` (el clon usa la URL en minúsculas); el push
  funciona igual.

## Estado al cierre de S2

- H01 COMPLETADO: los Redmi Buds 6 Lite envían batería de izquierdo,
  derecho y estuche con bit de carga (`+XIAOMI` de 7 elementos, solo
  al conectar) y la posición de cada auricular en tiempo real (trama
  `0A{XX}`). Los dispositivos AliExpress solo dan el porcentaje único
  Apple o nada.
- Sin cambios de código en S2: la APK publicada sigue siendo la v2 de
  diagnóstico.
