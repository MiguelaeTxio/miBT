# RESUMPTION_POINT — miBT

**Hito EN PROGRESO:** Hito 01 — Visor de diagnóstico y primeras
notificaciones (`DOCS/ANNEX_H01.md`).

## Próximos pasos concretos

Ver "HOJA DE RUTA PARA LA SIGUIENTE SESIÓN" en el anexo del Hito 01,
y los "DATOS REALES RECOGIDOS (S1)" que lo preceden.

## Cómo arrancar la próxima sesión de miBT

- Arranque normal con `newflow-android-pisa`: el repositorio ya tiene
  commits, así que `git rev-parse HEAD` funciona y fija
  `{SESSION_START_COMMIT}` (último commit de S1: ver `git log`).
- Chat nuevo: enlazar `miBT` con `add_repo` (access push).
- Secretos de GitHub Actions ya creados: `DEBUG_KEYSTORE_BASE64`,
  `PA_API_TOKEN`, `PA_USERNAME`, `RELEASES_REPO_TOKEN`. Repositorio de
  Releases `miBTReleases` creado.

## Nota de contexto

S1 se hizo dentro de un chat dedicado a otro proyecto
(AperturasAjedrez), sin PISA ni PCS propios de miBT: no hay registro
`com-pah` de miBT. La próxima sesión de miBT es la primera con
protocolo completo. Estado al cierre de S1: APK v2 publicada en
`miBTReleases`; recogido el log del auricular "TWS" (un único
porcentaje por HFP/Apple, sin izquierdo/derecho/estuche); pendientes
los logs de Xiaomi Buds 6 y del resto de dispositivos.
