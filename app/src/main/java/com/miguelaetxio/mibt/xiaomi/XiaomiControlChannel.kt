package com.miguelaetxio.mibt.xiaomi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.miguelaetxio.mibt.LogStore
import com.miguelaetxio.mibt.XiaomiBattery
import com.miguelaetxio.mibt.XiaomiPosition
import com.miguelaetxio.mibt.XiaomiProtocol
import java.io.ByteArrayOutputStream
import java.util.UUID

// Xiaomi's own control channel over RFCOMM 0xFD2D (confirmed on Redmi Buds 6 Lite
// in S3). Message layout and handshake follow Gadgetbridge (AGPL-3.0-or-later, see
// README.md): FE DC BA | type | opcode | len(2) | [status] | seq | payload | EF.
// After authenticating, the buds report battery (REPORT_STATUS 0x0E) and bud
// position (NOTIFY_CONFIG 0xF4, index 0x0C) on every change.
// ---
// Canal de control propio de Xiaomi por RFCOMM 0xFD2D (confirmado en Redmi Buds 6
// Lite en S3). Formato y apretón de manos según Gadgetbridge (AGPL-3.0-or-later,
// ver README.md): FE DC BA | tipo | opcode | long(2) | [estado] | seq | carga | EF.
// Tras autenticarse, los auriculares informan de la batería (REPORT_STATUS 0x0E) y
// de la posición (NOTIFY_CONFIG 0xF4, índice 0x0C) en cada cambio.
@SuppressLint("MissingPermission")
class XiaomiControlChannel(
    private val context: Context,
    private val device: BluetoothDevice,
    private val listener: Listener
) {

    interface Listener {
        fun onBattery(address: String, battery: XiaomiBattery)
        fun onPosition(address: String, position: XiaomiPosition)
    }

    companion object {
        private const val TAG = "XIAOMI-CTRL"
        private val UUID_CTRL: UUID = UUID.fromString("0000fd2d-0000-1000-8000-00805f9b34fb")
        private const val RETRY_MS = 15_000L

        private const val T_RESPONSE = 0x04
        private const val T_PHONE_REQUEST = 0xC4

        private const val OP_DEVICE_INFO = 0x02
        private const val OP_RUN_INFO = 0x09
        private const val OP_REPORT_STATUS = 0x0E
        private const val OP_AUTH_CHALLENGE = 0x50
        private const val OP_AUTH_CONFIRM = 0x51
        private const val OP_NOTIFY_CONFIG = 0xF4
    }

    @Volatile private var stopped = false
    @Volatile private var socket: BluetoothSocket? = null
    private var seq = 0

    fun start() {
        Thread({ loop() }, "mibt-xiaomi-ctrl").start()
    }

    fun stop() {
        stopped = true
        try { socket?.close() } catch (_: Exception) {}
    }

    // Reconnects while the device stays connected; the channel can refuse while
    // another app (e.g. Xiaomi Earbuds) holds it.
    // ---
    // Reconecta mientras el dispositivo siga conectado; el canal puede rechazar la
    // conexión mientras otra app (p. ej. Xiaomi Earbuds) lo tenga ocupado.
    private fun loop() {
        while (!stopped) {
            try {
                val s = device.createRfcommSocketToServiceRecord(UUID_CTRL)
                socket = s
                s.connect()
                LogStore.log(context, TAG, "conectado")
                session(s)
            } catch (t: Throwable) {
                if (!stopped) LogStore.log(context, TAG, "sin canal: ${t.javaClass.simpleName} ${t.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
            if (stopped) break
            try { Thread.sleep(RETRY_MS) } catch (_: InterruptedException) { break }
        }
        LogStore.log(context, TAG, "detenido")
    }

    private fun session(s: BluetoothSocket) {
        seq = 0
        val out = s.outputStream
        val challenge = XiaomiAuth.getRandomChallenge()
        send(out, T_PHONE_REQUEST, OP_AUTH_CHALLENGE, nextSeq(), byteArrayOf(0x01) + challenge)

        val input = s.inputStream
        val pending = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!stopped) {
            val n = input.read(buffer)
            if (n < 0) break
            pending.write(buffer, 0, n)
            var data = pending.toByteArray()
            while (true) {
                val start = indexOfHeader(data)
                if (start < 0) { data = ByteArray(0); break }
                if (data.size - start < 8) { data = data.copyOfRange(start, data.size); break }
                val len = ((data[start + 5].toInt() and 0xFF) shl 8) or (data[start + 6].toInt() and 0xFF)
                val total = 8 + len
                if (data.size - start < total) { data = data.copyOfRange(start, data.size); break }
                handle(out, data.copyOfRange(start, start + total))
                data = data.copyOfRange(start + total, data.size)
            }
            pending.reset()
            pending.write(data)
        }
        LogStore.log(context, TAG, "canal cerrado")
    }

    private fun handle(out: java.io.OutputStream, msg: ByteArray) {
        LogStore.log(context, TAG, "recibido ${hex(msg)}")
        val type = msg[3].toInt() and 0xFF
        val opcode = msg[4].toInt() and 0xFF
        val isRequest = type and 0x40 != 0
        val payloadOffset = if (isRequest) 8 else 9
        if (msg.size < payloadOffset + 1) return
        val msgSeq = msg[payloadOffset - 1].toInt() and 0xFF
        val payload = msg.copyOfRange(payloadOffset, msg.size - 1)

        when {
            opcode == OP_AUTH_CHALLENGE && !isRequest ->
                send(out, T_PHONE_REQUEST, OP_AUTH_CONFIRM, nextSeq(), byteArrayOf(0x01, 0x00))
            opcode == OP_AUTH_CHALLENGE && isRequest && payload.size >= 17 -> {
                val response = XiaomiAuth().computeChallengeResponse(payload.copyOfRange(1, 17))
                send(out, T_RESPONSE, OP_AUTH_CHALLENGE, msgSeq, byteArrayOf(0x01) + response)
            }
            opcode == OP_AUTH_CONFIRM && isRequest -> {
                send(out, T_RESPONSE, OP_AUTH_CONFIRM, msgSeq, byteArrayOf(0x01))
                LogStore.log(context, TAG, "autenticado")
                val all = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
                send(out, T_PHONE_REQUEST, OP_DEVICE_INFO, nextSeq(), all)
                send(out, T_PHONE_REQUEST, OP_RUN_INFO, nextSeq(), all)
            }
            opcode == OP_DEVICE_INFO && !isRequest ->
                forEachTlv(payload, indexAt = 1) { index, value ->
                    if (index == 0x07 && value.size >= 3) battery(value)
                }
            opcode == OP_REPORT_STATUS && isRequest -> {
                forEachTlv(payload, indexAt = 1) { index, value ->
                    if (index == 0x00 && value.size >= 3) battery(value)
                }
                send(out, T_RESPONSE, OP_REPORT_STATUS, msgSeq, ByteArray(0))
            }
            opcode == OP_NOTIFY_CONFIG && isRequest -> {
                forEachTlv(payload, indexAt = 2) { index, value ->
                    if (index == 0x0C && value.isNotEmpty()) {
                        val flags = value[0].toInt() and 0xFF
                        val position = XiaomiProtocol.positionFromFlags(flags)
                        LogStore.log(context, TAG, "posición flags=%02X %s".format(flags, position))
                        listener.onPosition(device.address, position)
                    }
                }
                send(out, T_RESPONSE, OP_NOTIFY_CONFIG, msgSeq, ByteArray(0))
            }
        }
    }

    // Battery bytes in order left, right, case (same encoding as the +XIAOMI list).
    // ---
    // Bytes de batería en orden izquierdo, derecho, estuche (misma codificación
    // que la lista +XIAOMI).
    private fun battery(value: ByteArray) {
        val b = XiaomiBattery(
            left = XiaomiProtocol.cell(value[0].toInt() and 0xFF),
            right = XiaomiProtocol.cell(value[1].toInt() and 0xFF),
            case = XiaomiProtocol.cell(value[2].toInt() and 0xFF)
        )
        LogStore.log(context, TAG, "batería $b")
        listener.onBattery(device.address, b)
    }

    // TLV list: [len][...]; the index byte sits at offset indexAt inside each entry
    // and the value follows it. len counts the bytes after itself.
    // ---
    // Lista TLV: [long][...]; el byte de índice está en el desplazamiento indexAt de
    // cada entrada y el valor va detrás. long cuenta los bytes que le siguen.
    private fun forEachTlv(payload: ByteArray, indexAt: Int, action: (Int, ByteArray) -> Unit) {
        var i = 0
        while (i < payload.size) {
            val len = payload[i].toInt() and 0xFF
            val end = i + 1 + len
            if (len < indexAt || end > payload.size) break
            val index = payload[i + indexAt].toInt() and 0xFF
            action(index, payload.copyOfRange(i + indexAt + 1, end))
            i = end
        }
    }

    private fun send(out: java.io.OutputStream, type: Int, opcode: Int, seq: Int, payload: ByteArray) {
        val isRequest = type and 0x40 != 0
        val len = payload.size + if (isRequest) 1 else 2
        val head = byteArrayOf(
            0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(),
            type.toByte(), opcode.toByte(), (len shr 8).toByte(), len.toByte()
        )
        val mid = if (isRequest) byteArrayOf(seq.toByte()) else byteArrayOf(0x00, seq.toByte())
        val msg = head + mid + payload + byteArrayOf(0xEF.toByte())
        out.write(msg)
        out.flush()
        LogStore.log(context, TAG, "enviado ${hex(msg)}")
    }

    private fun nextSeq(): Int = (seq++) and 0xFF

    private fun indexOfHeader(data: ByteArray): Int {
        for (i in 0..data.size - 3) {
            if (data[i] == 0xFE.toByte() && data[i + 1] == 0xDC.toByte() && data[i + 2] == 0xBA.toByte()) return i
        }
        return -1
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}
