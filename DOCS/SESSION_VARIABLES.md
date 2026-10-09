# MIBT — VARIABLES DE SESIÓN (NewFlow Android)

| Variable | Valor |
|---|---|
| `PROJECT_ID` | miBT |
| `ANDROID_APP_NAME` | miBT |
| `ANDROID_PACKAGE` | `com.miguelaetxio.mibt` |
| `ANDROID_GITHUB_REPO` | `https://github.com/MiguelaeTxio/miBT.git` |
| `ANDROID_GITHUB_OWNER` | MiguelaeTxio |
| `ANDROID_GITHUB_BRANCH` | main |
| `MASTER_DOCUMENT_PATH` | `DOCS/MASTER_DOCUMENT.md` |
| `RESUMPTION_POINT_PATH` | `DOCS/RESUMPTION_POINT.md` |
| `APK_DEPLOY_PATH` (PythonAnywhere, gestionado por el workflow) | `/home/MiguelAeTxio/ANDROID/miBT/apk/miBT.apk` |
| `RELEASES_REPO` (Releases públicas para el checker de actualizaciones, propio de este proyecto) | `https://github.com/MiguelaeTxio/miBTReleases` |

## Secrets de GitHub Actions (solo el nombre, nunca el valor)

- `PA_API_TOKEN`
- `PA_USERNAME`
- `DEBUG_KEYSTORE_BASE64` (keystore de debug propia de miBT)
- `RELEASES_REPO_TOKEN` (permiso *Contents: Read and write*
  únicamente sobre `miBTReleases`, usado por
  `softprops/action-gh-release`)

## Notas

- Mismo patrón que AperturasAjedrez: Release en repositorio propio
  + `manifest.json` + actualizador in-app. Primera instalación:
  `https://github.com/MiguelaeTxio/miBTReleases/releases/latest/download/miBT.apk`
- Ningún token vive en este archivo ni en ningún archivo del
  repositorio.
