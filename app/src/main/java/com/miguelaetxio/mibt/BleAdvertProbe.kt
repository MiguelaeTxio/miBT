package com.miguelaetxio.mibt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

// Diagnostic BLE scan (H03, TWS earbuds). The TWS identify themselves as Apple
// accessories over HFP (+XAPL, +IPHONEACCEV); AirPods-style earbuds also broadcast
// Apple "proximity pairing" adverts (manufacturer 0x004C, type 0x07) carrying
// left/right/case battery, charging and in-ear state. Whether these TWS do is
// unknown: this logs every such advert, plus any advert named "TWS", for 60 s after
// a non-Xiaomi device connects. Nothing is decoded or shown yet (§4.6).
// ---
// Escaneo BLE de diagnóstico (H03, auriculares TWS). Los TWS se identifican como
// accesorios Apple por HFP (+XAPL, +IPHONEACCEV); los auriculares tipo AirPods
// emiten además anuncios Apple de "proximity pairing" (fabricante 0x004C, tipo 0x07)
// con batería de izquierdo/derecho/estuche, carga y si están puestos. No se sabe si
// estos TWS lo hacen: esto registra cada anuncio así, y cualquiera llamado "TWS",
// durante 60 s tras conectarse un dispositivo que no es Xiaomi. Aún no se descifra
// ni se muestra nada (§4.6).
@SuppressLint("MissingPermission")
class BleAdvertProbe(private val context: Context) {

    companion object {
        private const val TAG = "BLE-ADV"
        private const val APPLE_ID = 0x004C
        private const val SCAN_MS = 60_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val seen = HashSet<String>()
    private var running = false

    // Totals logged when the scan ends, so "nothing logged" can be told apart from
    // "the scan received nothing" (2026-10-18 log: 19 scans, no line either way).
    // ---
    // Totales registrados al terminar, para distinguir "no se registró nada" de "el
    // escaneo no recibió nada" (log del 2026-10-18: 19 escaneos, sin ninguna línea).
    private var total = 0
    private val appleTypes = HashMap<Int, Int>()
    private val addresses = HashSet<String>()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val apple = record.getManufacturerSpecificData(APPLE_ID)
            total++
            addresses.add(result.device.address)
            if (apple != null && apple.isNotEmpty()) {
                val type = apple[0].toInt() and 0xFF
                appleTypes[type] = (appleTypes[type] ?: 0) + 1
            }
            val name = record.deviceName
            val isProximity = apple != null && apple.isNotEmpty() && apple[0].toInt() == 0x07
            val isTws = name != null && name.contains("TWS", ignoreCase = true)
            if (!isProximity && !isTws) return
            val payload = apple?.joinToString("") { "%02X".format(it) } ?: "-"
            // Same address and payload logged once per scan.
            // ---
            // Misma dirección y carga útil, una sola vez por escaneo.
            if (!seen.add("${result.device.address}|$payload")) return
            val all = (0 until record.manufacturerSpecificData.size()).joinToString(",") {
                val id = record.manufacturerSpecificData.keyAt(it)
                val data = record.manufacturerSpecificData.valueAt(it)
                "%04X:%s".format(id, data.joinToString("") { b -> "%02X".format(b) })
            }
            LogStore.log(
                context, TAG,
                "${result.device.address} nombre=${name ?: "-"} rssi=${result.rssi} apple=$payload fabricantes=[$all]"
            )
        }

        override fun onScanFailed(errorCode: Int) {
            LogStore.log(context, TAG, "escaneo fallido, código=$errorCode")
            running = false
        }
    }

    fun start(adapter: BluetoothAdapter?) {
        if (running) return
        val scanner = adapter?.bluetoothLeScanner ?: run {
            LogStore.log(context, TAG, "sin escáner BLE")
            return
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // Filtered scan: Android pauses unfiltered scans while the screen is off.
        // ---
        // Escaneo con filtros: Android pausa los escaneos sin filtro con la pantalla apagada.
        val filters = listOf(
            ScanFilter.Builder().setManufacturerData(APPLE_ID, ByteArray(0)).build(),
            ScanFilter.Builder().setDeviceName("TWS").build()
        )
        try {
            scanner.startScan(filters, settings, callback)
        } catch (t: Throwable) {
            LogStore.log(context, TAG, "no se pudo escanear: ${t.javaClass.simpleName} ${t.message}")
            return
        }
        running = true
        seen.clear()
        total = 0
        appleTypes.clear()
        addresses.clear()
        LogStore.log(context, TAG, "escaneo iniciado (${SCAN_MS / 1000} s)")
        handler.postDelayed({ stop(adapter) }, SCAN_MS)
    }

    fun stop(adapter: BluetoothAdapter?) {
        if (!running) return
        running = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Throwable) {
        }
        val types = appleTypes.entries.sortedBy { it.key }
            .joinToString(",") { "%02X:%d".format(it.key, it.value) }
        LogStore.log(
            context, TAG,
            "escaneo terminado: $total anuncios de ${addresses.size} dispositivo(s), tipos Apple=[$types]"
        )
    }
}
