package com.miguelaetxio.mibt.update

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

sealed class UpdateCheckResult {
    object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
