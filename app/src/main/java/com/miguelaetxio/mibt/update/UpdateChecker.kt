package com.miguelaetxio.mibt.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Comprueba, descarga e instala actualizaciones publicadas en el
 * repositorio publico miBTReleases -- mismo patron que
 * MiMoo con AndroidReleases (manifest.json + alias
 * releases/latest/download/..., FileProvider + Intent(ACTION_VIEW)
 * para instalar), pero sin Retrofit/Hilt: la app aun no tiene esa
 * arquitectura y no merece la pena introducirla solo para esto.
 *
 * IMPORTANTE: usa un repositorio de Releases PROPIO
 * (miBTReleases), nunca el AndroidReleases de MiMoo --
 * el alias releases/latest/download apunta a la Release mas reciente
 * de TODO el repositorio, asi que compartirlo entre dos apps rompe
 * el checker de la que no acaba de publicar. Ver incidencia S1 en
 * DOCS/ATTACHEDS/APERTURASAJEDREZ_ANNEX_H01.md.
 */
class UpdateChecker(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    fun checkForUpdate(currentVersionCode: Int, onResult: (UpdateCheckResult) -> Unit) {
        executor.execute {
            val result = try {
                val raw = httpGetString(MANIFEST_URL)
                val obj = JSONObject(raw)
                val manifest = UpdateManifest(
                    versionCode = obj.getInt("versionCode"),
                    versionName = obj.getString("versionName"),
                    apkUrl = obj.getString("apkUrl")
                )
                if (manifest.versionCode > currentVersionCode) {
                    UpdateCheckResult.UpdateAvailable(manifest)
                } else {
                    UpdateCheckResult.UpToDate
                }
            } catch (e: Exception) {
                UpdateCheckResult.Error(
                    e.message ?: "No se pudo comprobar si hay actualizaciones."
                )
            }
            mainHandler.post { onResult(result) }
        }
    }

    fun downloadApk(
        manifest: UpdateManifest,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val updatesDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
                val apkFile = File(updatesDir, "miBT-update.apk")

                val connection = URL(manifest.apkUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.connect()

                val totalBytes = connection.contentLengthLong
                var bytesDownloaded = 0L

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            val downloadedSoFar = bytesDownloaded
                            mainHandler.post { onProgress(downloadedSoFar, totalBytes) }
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                mainHandler.post { onSuccess(uri) }
            } catch (e: Exception) {
                mainHandler.post {
                    onError(e.message ?: "Fallo al descargar la actualizacion.")
                }
            }
        }
    }

    /**
     * Sin este chequeo, el Intent de instalacion no hace absolutamente
     * nada si falta REQUEST_INSTALL_PACKAGES -- ni error ni aviso (bug
     * real ya sufrido y corregido en MiMoo, H07 PARTE 2). Devuelve
     * true si ya se puede instalar directamente; si no, manda a
     * Miguel Angel a la pantalla de Ajustes del sistema donde
     * concederlo y devuelve false.
     */
    fun ensureInstallPermissionOrRedirect(): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Si el propio Intent de Ajustes fallara (dispositivo muy
            // modificado), no hay mas alternativa que decirselo a
            // Miguel Angel -- lo hace la pantalla que llama a esto.
        }
        return false
    }

    fun launchInstall(apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun httpGetString(urlStr: String): String {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.connect()
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    companion object {
        private const val MANIFEST_URL =
            "https://github.com/MiguelaeTxio/miBTReleases/releases/latest/download/manifest.json"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val DOWNLOAD_BUFFER_SIZE = 8 * 1024
    }
}
