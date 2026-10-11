package com.miguelaetxio.mibt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.security.SecureRandom
import java.util.UUID

// Diagnostic probe of Xiaomi's own control channel (H03 step 8). Per Gadgetbridge
// (Redmi Buds 5 Pro) it is RFCOMM, messages framed FE DC BA ... EF, first message
// an auth challenge. The Redmi Buds 6 Lite advertise other UUIDs, so each candidate
// is tried and every byte received is logged. Nothing is decoded or shown yet.
// ---
// Sonda de diagnóstico del canal de control propio de Xiaomi (H03 paso 8). Según
// Gadgetbridge (Redmi Buds 5 Pro) es RFCOMM, mensajes enmarcados FE DC BA ... EF, y
// el primero es un reto de autenticación. Los Redmi Buds 6 Lite anuncian otros UUID,
// así que se prueba cada candidato y se registra todo byte recibido. Aún no se
// descifra ni se muestra nada.
@SuppressLint("MissingPermission")
class XiaomiRfcommProbe(private val context: Context, private val device: BluetoothDevice) {

    companion object {
        private const val TAG = "RFCOMM"
        private const val LISTEN_MS = 90_000L

        private val CANDIDATES = listOf(
            "0000fd2d-0000-1000-8000-00805f9b34fb",
            "df21fe2c-2515-4fdb-8886-f12c4d67927c",
            "8a482a08-5507-42ac-b673-a88df48b3fc7",
            "00001101-0000-1000-8000-00805f9b34fb"
        ).map { UUID.fromString(it) }
    }

    fun start() {
        Thread({ run() }, "mibt-rfcomm-probe").start()
    }

    private fun run() {
        for (uuid in CANDIDATES) {
            if (tryUuid(uuid)) return
        }
        LogStore.log(context, TAG, "ningún UUID candidato aceptó conexión")
    }

    // Returns true when the socket connected (the probe then stops trying others).
    // ---
    // Devuelve true si el socket conectó (la sonda deja de probar los demás).
    private fun tryUuid(uuid: UUID): Boolean {
        val socket = try {
            device.createRfcommSocketToServiceRecord(uuid)
        } catch (t: Throwable) {
            LogStore.log(context, TAG, "$uuid no se pudo crear socket: ${t.javaClass.simpleName}")
            return false
        }
        try {
            socket.connect()
        } catch (t: Throwable) {
            LogStore.log(context, TAG, "$uuid conexión rechazada: ${t.javaClass.simpleName} ${t.message}")
            try { socket.close() } catch (_: Exception) {}
            return false
        }
        LogStore.log(context, TAG, "$uuid conectado")
        try {
            val challenge = authChallenge()
            socket.outputStream.write(challenge)
            socket.outputStream.flush()
            LogStore.log(context, TAG, "$uuid enviado reto: ${hex(challenge, challenge.size)}")

            val input = socket.inputStream
            val buffer = ByteArray(4096)
            val deadline = System.currentTimeMillis() + LISTEN_MS
            // Close the socket at the deadline so a blocking read() returns.
            // ---
            // Cierra el socket al vencer el plazo para que un read() bloqueado vuelva.
            Thread({
                Thread.sleep(LISTEN_MS)
                try { socket.close() } catch (_: Exception) {}
            }, "mibt-rfcomm-timeout").start()
            while (System.currentTimeMillis() < deadline) {
                val n = input.read(buffer)
                if (n < 0) break
                LogStore.log(context, TAG, "$uuid recibido $n bytes: ${hex(buffer, n)}")
            }
        } catch (t: Throwable) {
            LogStore.log(context, TAG, "$uuid fin de escucha: ${t.javaClass.simpleName} ${t.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
            LogStore.log(context, TAG, "$uuid cerrado")
        }
        return true
    }

    // FE DC BA | type C4 (phone request) | opcode 50 (auth challenge) | length 00 12 |
    // seq 00 | 01 + 16 random bytes | EF. Same framing as Gadgetbridge's Message.encode().
    // ---
    // FE DC BA | tipo C4 (petición del móvil) | opcode 50 (reto de autenticación) |
    // longitud 00 12 | seq 00 | 01 + 16 bytes aleatorios | EF. Mismo enmarcado que
    // Message.encode() de Gadgetbridge.
    private fun authChallenge(): ByteArray {
        val rnd = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val header = byteArrayOf(
            0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(),
            0xC4.toByte(), 0x50, 0x00, 0x12, 0x00, 0x01
        )
        return header + rnd + byteArrayOf(0xEF.toByte())
    }

    private fun hex(bytes: ByteArray, n: Int): String =
        (0 until n).joinToString("") { "%02X".format(bytes[it]) }
}
