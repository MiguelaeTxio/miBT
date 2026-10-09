package com.miguelaetxio.mibt

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {

    private const val FILE_NAME = "mibt-log.txt"
    private const val MAX_BYTES = 512 * 1024L

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun log(context: Context, tag: String, message: String) {
        try {
            val f = file(context)
            // Trim the file when it grows too much, keeping the tail.
            // ---
            // Recorta el archivo cuando crece demasiado, conservando el final.
            if (f.exists() && f.length() > MAX_BYTES) {
                val tail = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(tail)
            }
            f.appendText("${format.format(Date())} [$tag] $message\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun tail(context: Context, lines: Int): String {
        return try {
            val f = file(context)
            if (!f.exists()) "" else f.readLines().takeLast(lines).joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }

    @Synchronized
    fun clear(context: Context) {
        try {
            file(context).writeText("")
        } catch (_: Exception) {
        }
    }
}
