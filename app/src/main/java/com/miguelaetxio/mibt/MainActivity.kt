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
import com.miguelaetxio.mibt.update.UpdateCheckResult
import com.miguelaetxio.mibt.update.UpdateChecker

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var updateStatus: TextView
    private lateinit var installButton: Button
    private lateinit var updateChecker: UpdateChecker
    private var pendingApkUri: android.net.Uri? = null

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
        if (Build.VERSION.SDK_INT >= 31) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
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
