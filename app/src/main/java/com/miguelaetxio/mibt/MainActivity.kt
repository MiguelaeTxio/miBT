package com.miguelaetxio.mibt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.miguelaetxio.mibt.drive.DriveLogUploader
import com.miguelaetxio.mibt.update.UpdateCheckResult
import com.miguelaetxio.mibt.update.UpdateChecker

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var updateStatus: TextView
    private lateinit var installButton: Button
    private lateinit var updateChecker: UpdateChecker
    private var pendingApkUri: android.net.Uri? = null
    private lateinit var driveStatus: TextView

    companion object {
        private const val REQ_DRIVE_AUTH = 2
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 16f }
        logView = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

        updateChecker = UpdateChecker(this)
        updateStatus = TextView(this).apply { textSize = 14f }
        installButton = button("Instalar actualización") { onInstallClicked() }
        installButton.visibility = android.view.View.GONE

        root.addView(status)
        root.addView(button("Permisos y arrancar") { startMonitor() })
        root.addView(button("Parar") {
            stopService(Intent(this, BtMonitorService::class.java))
            showLog()
        })
        root.addView(button("Actualizar log") { showLog() })
        root.addView(button("Compartir log") { shareLog() })
        root.addView(button("Subir logs a Drive") { onUploadLogsClicked() })
        driveStatus = TextView(this).apply { textSize = 14f }
        root.addView(driveStatus)
        root.addView(button("Borrar log") {
            LogStore.clear(this)
            showLog()
        })
        root.addView(button("Buscar actualizaciones") { onCheckUpdateClicked() })
        root.addView(updateStatus)
        root.addView(installButton)
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        showLog()
        // Back from system settings after granting "install unknown apps":
        // retry the install directly.
        // ---
        // Al volver de Ajustes tras conceder "instalar apps desconocidas":
        // reintenta la instalación directamente.
        if (pendingApkUri != null && updateChecker.ensureInstallPermissionOrRedirect()) {
            onInstallClicked()
        }
    }

    private fun onCheckUpdateClicked() {
        installButton.visibility = android.view.View.GONE
        updateStatus.text = "Comprobando actualizaciones..."
        updateChecker.checkForUpdate(BuildConfig.VERSION_CODE) { result ->
            when (result) {
                is UpdateCheckResult.UpToDate -> updateStatus.text = "Ya tienes la última versión"
                is UpdateCheckResult.Error -> updateStatus.text = "Error: ${result.message}"
                is UpdateCheckResult.UpdateAvailable -> {
                    updateStatus.text = "Descargando versión ${result.manifest.versionName}..."
                    updateChecker.downloadApk(
                        result.manifest,
                        onProgress = { _, _ -> },
                        onSuccess = { uri ->
                            pendingApkUri = uri
                            updateStatus.text = "Descarga completa"
                            installButton.visibility = android.view.View.VISIBLE
                        },
                        onError = { message -> updateStatus.text = "Error: $message" }
                    )
                }
            }
        }
    }

    private fun onInstallClicked() {
        val uri = pendingApkUri ?: return
        if (!updateChecker.ensureInstallPermissionOrRedirect()) {
            updateStatus.text = "Concede el permiso de instalar apps desconocidas y vuelve"
            return
        }
        updateChecker.launchInstall(uri)
    }

    private fun missingPermissions(): List<String> {
        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        return needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startMonitor() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, BtMonitorService::class.java))
        showLog()
    }

    @Deprecated("Deprecated in Android 11")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (missingPermissions().isEmpty()) startMonitor() else showLog()
    }

    private fun showLog() {
        val missing = missingPermissions()
        status.text = "miBT ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n" +
            if (missing.isEmpty()) "Permisos concedidos" else "Faltan permisos: ${missing.size}"
        logView.text = LogStore.tail(this, 80)
    }

    // Same flow as MiMoo: AuthorizationClient with the drive.file scope (the app
    // only sees what it creates); consent dialog only the first time.
    // ---
    // Mismo flujo que MiMoo: AuthorizationClient con el scope drive.file (la app
    // solo ve lo que crea); el diálogo de consentimiento solo la primera vez.
    private fun onUploadLogsClicked() {
        driveStatus.text = "Autorizando Drive..."
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    try {
                        startIntentSenderForResult(pending.intentSender, REQ_DRIVE_AUTH, null, 0, 0, 0)
                    } catch (e: Exception) {
                        driveError("no se pudo abrir el consentimiento", e)
                    }
                } else {
                    val token = result.accessToken
                    if (token != null) uploadLogs(token) else driveStatus.text = "Error: Drive no devolvió token"
                }
            }
            .addOnFailureListener { e -> driveError("autorización rechazada", e) }
    }

    @Deprecated("Deprecated in Android 11")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_DRIVE_AUTH) return
        // Read the result from the Intent without checking resultCode first
        // (MiMoo S006: checking it discarded valid authorizations).
        // ---
        // Se lee el resultado del Intent sin mirar antes resultCode (MiMoo S006:
        // comprobarlo descartaba autorizaciones válidas).
        try {
            val result = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token != null) uploadLogs(token) else driveStatus.text = "Error: Drive no devolvió token"
        } catch (e: Exception) {
            driveError("consentimiento no concedido", e)
        }
    }

    private fun uploadLogs(token: String) {
        driveStatus.text = "Subiendo logs a Drive..."
        Thread {
            try {
                DriveLogUploader(this).upload(token)
                runOnUiThread {
                    driveStatus.text = "Logs subidos a \"${DriveLogUploader.FOLDER_NAME}\""
                    showLog()
                }
            } catch (e: Exception) {
                runOnUiThread { driveError("subida fallida", e) }
            }
        }.start()
    }

    private fun driveError(what: String, e: Exception) {
        val detail = if (e is com.google.android.gms.common.api.ApiException) {
            "${e.statusCode} ${e.status.statusMessage ?: ""}"
        } else {
            "${e.javaClass.simpleName} ${e.message ?: ""}"
        }
        LogStore.log(this, "DRIVE", "$what: $detail")
        driveStatus.text = "Error: $what ($detail)"
        showLog()
    }

    private fun shareLog() {
        val file = LogStore.file(this)
        if (!file.exists() || file.length() == 0L) {
            status.text = "El log está vacío"
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Compartir log de miBT"))
    }
}
