package com.miguelaetxio.mibt.drive

import android.content.Context
import android.os.Build
import com.miguelaetxio.mibt.BuildConfig
import com.miguelaetxio.mibt.LogStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Uploads miBT's logs to the Drive folder "miBT - Intercambio Claude" so Claude can
// read them (com-actions-relay §7, same pattern as MiMoo). Fixed-name files are
// overwritten in place, so the folder always holds exactly the last upload; the
// manifest goes last, so a failed upload leaves the previous manifest (old time).
// Closed list of files: never "every file in filesDir".
// ---
// Sube los logs de miBT a la carpeta de Drive "miBT - Intercambio Claude" para que
// Claude los lea (com-actions-relay §7, mismo patrón que MiMoo). Los archivos de
// nombre fijo se sobrescriben en su sitio, así que la carpeta contiene siempre
// exactamente la última subida; el manifiesto va el último, de modo que una subida
// fallida deja el manifiesto anterior (hora vieja). Lista cerrada de archivos: nunca
// "todo lo que haya en filesDir".
class DriveLogUploader(private val context: Context) {

    companion object {
        const val FOLDER_NAME = "miBT - Intercambio Claude"
        const val MANIFEST_NAME = "mibt_logs_manifest.txt"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val TEXT_MIME = "text/plain"
        private const val API = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
        private val LOG_FILE_NAMES = listOf("mibt-log.txt")
    }

    // Blocking: call it off the main thread.
    // ---
    // Bloqueante: llamarla fuera del hilo principal.
    fun upload(accessToken: String) {
        val found = LinkedHashMap<String, String>()
        val missing = ArrayList<String>()
        for (name in LOG_FILE_NAMES) {
            val file = java.io.File(context.filesDir, name)
            if (file.exists()) found[name] = file.readText() else missing.add(name)
        }
        val folderId = ensureFolder(accessToken)
        for ((name, content) in found) {
            putFile(accessToken, folderId, name, content)
        }
        putFile(accessToken, folderId, MANIFEST_NAME, manifest(found, missing))
        LogStore.log(context, "DRIVE", "logs subidos a '$FOLDER_NAME' (${found.size} archivo(s) + manifiesto)")
    }

    private fun manifest(found: Map<String, String>, missing: List<String>): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("Subida: $stamp")
            appendLine("Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            appendLine()
            appendLine("Archivos subidos (${found.size}):")
            found.forEach { (name, content) -> appendLine("  $name  ${content.toByteArray().size} bytes") }
            appendLine()
            appendLine("No encontrados en el dispositivo (${missing.size}):")
            missing.forEach { appendLine("  $it") }
        }
    }

    private fun ensureFolder(token: String): String {
        findId(token, "mimeType = '$FOLDER_MIME' and name = '$FOLDER_NAME' and trashed = false")
            ?.let { return it }
        val body = JSONObject().put("name", FOLDER_NAME).put("mimeType", FOLDER_MIME)
        return JSONObject(request(token, "POST", "$API?fields=id", body.toString(), "application/json"))
            .getString("id")
    }

    private fun putFile(token: String, folderId: String, name: String, content: String) {
        val id = findId(token, "'$folderId' in parents and name = '$name' and trashed = false")
            ?: JSONObject(
                request(
                    token, "POST", "$API?fields=id",
                    JSONObject().put("name", name).put("mimeType", TEXT_MIME)
                        .put("parents", JSONArray().put(folderId)).toString(),
                    "application/json"
                )
            ).getString("id")
        // HttpURLConnection has no PATCH: Google APIs accept POST + method override.
        // ---
        // HttpURLConnection no tiene PATCH: las APIs de Google aceptan POST + override.
        request(token, "PATCH", "$UPLOAD_API/$id?uploadType=media&fields=id", content, "$TEXT_MIME; charset=utf-8")
    }

    private fun findId(token: String, query: String): String? {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(request(token, "GET", "$API?q=$q&fields=files(id)&spaces=drive", null, null))
        val files = json.optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun request(token: String, method: String, url: String, body: String?, contentType: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (method == "PATCH") {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("Drive HTTP $code: ${text.take(300)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
