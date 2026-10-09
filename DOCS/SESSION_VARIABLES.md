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

## Secrets de GitHub Actions (solo el nombre, nunca el valor)

- `PA_API_TOKEN`
- `PA_USERNAME`
- `DEBUG_KEYSTORE_BASE64` (keystore de debug propia de miBT)

## Notas

- Sin repositorio de Releases ni actualizador in-app por ahora.
- Ningún token vive en este archivo ni en ningún archivo del
  repositorio.
