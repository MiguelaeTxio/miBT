package com.miguelaetxio.mibt

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@SuppressLint("MissingPermission")
class BtMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "mibt_devices"
        const val SUMMARY_ID = 1
        private const val TAG = "SVC"
        private val BATTERY_SERVICE: UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL: UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        // Hidden system broadcast, only logged if it reaches us.
        // ---
        // Broadcast oculto del sistema, solo se registra si nos llega.
        private const val ACTION_BATTERY_LEVEL_CHANGED =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL =
            "android.bluetooth.device.extra.BATTERY_LEVEL"

        // Bluetooth SIG company ids whose HFP vendor events we listen to:
        // Apple, Samsung, Huawei, Xiaomi, Google, Sony.
        // ---
        // Company ids del Bluetooth SIG cuyos eventos HFP escuchamos:
        // Apple, Samsung, Huawei, Xiaomi, Google, Sony.
        private val COMPANY_IDS = intArrayOf(76, 117, 637, 911, 224, 301)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var adapter: BluetoothAdapter? = null
    private val proxies = HashMap<Int, BluetoothProfile>()

    private val vendorBattery = HashMap<String, Int>()

    // Devices whose vendorBattery value came from +IPHONEACCEV, which only
    // reports steps of 10 % (0..9), so the figure is approximate.
    // ---
    // Dispositivos cuyo valor de vendorBattery viene de +IPHONEACCEV, que solo
    // informa en tramos de 10 % (0..9), así que la cifra es aproximada.
    private val approxBattery = HashSet<String>()

    // Last +XIAOMI battery list per device with its reception time (kept after
    // disconnecting) and last bud position (cleared when the device disconnects).
    // ---
    // Última lista de batería +XIAOMI por dispositivo con su hora de recepción (se
    // conserva al desconectar) y última posición de los auriculares (se borra al
    // desconectarse el dispositivo).
    private val xiaomiBattery = HashMap<String, Pair<XiaomiBattery, Long>>()
    private val xiaomiPosition = HashMap<String, XiaomiPosition>()
    private val lastSnapshot = HashMap<String, String>()
    private val gattProbed = HashSet<String>()
    private val notifiedIds = HashSet<Int>()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
            LogStore.log(this@BtMonitorService, TAG, "proxy conectado, perfil=$profile")
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            refresh()
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = deviceOf(intent)
            val action = intent.action ?: return
            if (action == ACTION_BATTERY_LEVEL_CHANGED) {
                val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                LogStore.log(context, "BATT-BROADCAST", "${describe(device)} nivel=$level")
                if (device != null && level in 0..100) {
                    vendorBattery[device.address] = level
                    approxBattery.remove(device.address)
                }
            } else {
                LogStore.log(context, "EVENT", "$action ${describe(device)}")
            }
            handler.postDelayed({ refresh() }, 800)
        }
    }

    private val vendorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = deviceOf(intent)
            val cmd = intent.getStringExtra(
                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD
            )
            val type = intent.getIntExtra(
                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1
            )
            val args = intent.getSerializableExtra(
                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS
            )
            val argsText = when (args) {
                is Array<*> -> args.joinToString(",") { it.toString() }
                null -> ""
                else -> args.toString()
            }
            LogStore.log(
                context, "HFP-VENDOR",
                "${describe(device)} cmd=$cmd tipo=$type args=[$argsText] categorias=${intent.categories}"
            )
            // +IPHONEACCEV: args = count, key1, value1, ...; key 1 = battery 0..9.
            // ---
            // +IPHONEACCEV: args = count, key1, valor1, ...; key 1 = batería 0..9.
            if (device != null && cmd == "+IPHONEACCEV" && args is Array<*>) {
                var i = 1
                while (i + 1 < args.size) {
                    if (args[i].toString() == "1") {
                        val v = args[i + 1].toString().toIntOrNull()
                        if (v != null && v in 0..9) {
                            vendorBattery[device.address] = (v + 1) * 10
                            approxBattery.add(device.address)
                        }
                    }
                    i += 2
                }
            }
            if (device != null && cmd == "+XIAOMI" && args is Array<*>) {
                XiaomiProtocol.parseBattery(args)?.let {
                    xiaomiBattery[device.address] = it to System.currentTimeMillis()
                }
                XiaomiProtocol.parsePosition(args)?.let {
                    xiaomiPosition[device.address] = it
                }
            }
            refresh()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildSummary(0)
        ServiceCompat.startForeground(
            this, SUMMARY_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        LogStore.log(
            this, TAG,
            "servicio iniciado; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "${Build.MANUFACTURER} ${Build.MODEL}"
        )

        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        adapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
        adapter?.getProfileProxy(this, profileListener, BluetoothProfile.A2DP)

        val stateFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
        }
        ContextCompat.registerReceiver(
            this, stateReceiver, stateFilter, ContextCompat.RECEIVER_EXPORTED
        )

        val vendorFilter = IntentFilter(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
        for (id in COMPANY_IDS) {
            vendorFilter.addCategory(
                BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + id
            )
        }
        ContextCompat.registerReceiver(
            this, vendorReceiver, vendorFilter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stateReceiver)
            unregisterReceiver(vendorReceiver)
        } catch (_: Exception) {
        }
        for ((profile, proxy) in proxies) {
            adapter?.closeProfileProxy(profile, proxy)
        }
        proxies.clear()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (id in notifiedIds) nm.cancel(id)
        notifiedIds.clear()
        LogStore.log(this, TAG, "servicio detenido")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Dispositivos Bluetooth", NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    private fun deviceOf(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun describe(device: BluetoothDevice?): String {
        if (device == null) return "(sin dispositivo)"
        val name = try { device.name } catch (_: Exception) { null }
        return "${name ?: "?"} (${device.address})"
    }

    private fun connectedDevices(): List<BluetoothDevice> {
        val seen = LinkedHashMap<String, BluetoothDevice>()
        for (proxy in proxies.values) {
            try {
                for (d in proxy.connectedDevices) seen[d.address] = d
            } catch (_: Exception) {
            }
        }
        return seen.values.toList()
    }

    // Hidden BluetoothDevice.getBatteryLevel() by reflection; -1 = unknown.
    // ---
    // BluetoothDevice.getBatteryLevel() oculto por reflexión; -1 = desconocido.
    private fun hiddenBattery(device: BluetoothDevice): String {
        return try {
            val m = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            val v = m.invoke(device) as Int
            if (v in 0..100) "$v" else "desconocida($v)"
        } catch (t: Throwable) {
            "no disponible(${t.javaClass.simpleName})"
        }
    }

    private fun refresh() {
        val devices = connectedDevices()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val current = HashSet<Int>()

        for (d in devices) {
            val addr = d.address
            val name = try { d.name } catch (_: Exception) { null } ?: addr
            val hidden = hiddenBattery(d)
            val hiddenPct = hidden.toIntOrNull()
            val vendor = vendorBattery[addr]
            val pct = vendor ?: hiddenPct

            val snapshot = "oculta=$hidden hfp=${vendor ?: "-"}"
            if (lastSnapshot[addr] != snapshot) {
                lastSnapshot[addr] = snapshot
                val uuids = try { d.uuids?.joinToString(",") { it.toString() } } catch (_: Exception) { "?" }
                LogStore.log(
                    this, "DEVICE",
                    "$name ($addr) clase=${d.bluetoothClass?.deviceClass} tipo=${d.type} " +
                        "$snapshot uuids=[$uuids]"
                )
            }
            if (gattProbed.add(addr)) probeGatt(d)

            val id = addr.hashCode()
            current.add(id)
            val title = name
            val line = when {
                pct == null -> "Batería: sin datos"
                vendor != null && addr in approxBattery ->
                    "Batería: ≈$pct% (HFP, en tramos de 10 %)"
                else -> "Batería: $pct%"
            }
            val detail = line + "\n" + xiaomiDetail(addr)
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setGroup("mibt")
                .build()
            nm.notify(id, n)
        }

        for (id in notifiedIds - current) nm.cancel(id)
        notifiedIds.clear()
        notifiedIds.addAll(current)
        val connected = devices.map { it.address }.toSet()
        gattProbed.retainAll(connected)
        xiaomiPosition.keys.retainAll(connected)
        nm.notify(SUMMARY_ID, buildSummary(devices.size))
    }

    // Per-element battery, reading time and bud position when +XIAOMI data exist;
    // otherwise the generic "no data" line. Nothing is invented (§4.6).
    // ---
    // Batería por elemento, hora de lectura y posición de los auriculares si hay
    // datos +XIAOMI; si no, la línea genérica "sin datos". Nada se inventa (§4.6).
    private fun xiaomiDetail(addr: String): String {
        val battery = xiaomiBattery[addr]
        val position = xiaomiPosition[addr]
        if (battery == null && position == null) {
            return "Izquierdo / derecho / estuche / carga: sin datos " +
                "(pendiente del protocolo del fabricante)"
        }
        val lines = ArrayList<String>()
        if (battery != null) {
            val (b, time) = battery
            lines.add("Izquierdo: ${cellText(b.left)}")
            lines.add("Derecho: ${cellText(b.right)}")
            lines.add("Estuche: ${cellText(b.case)}")
            val hour = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
            lines.add("Lectura de las $hour (al conectar)")
        } else {
            lines.add("Izquierdo / derecho / estuche: sin datos")
        }
        if (position != null) {
            lines.add(
                "Posición: izquierdo ${placeText(position.left)}, " +
                    "derecho ${placeText(position.right)}"
            )
        }
        return lines.joinToString("\n")
    }

    private fun cellText(cell: XiaomiCell?): String {
        if (cell == null) return "sin dato"
        return "${cell.percent}%" + if (cell.charging) " ⚡ cargando" else ""
    }

    private fun placeText(place: BudPlace): String = when (place) {
        BudPlace.IN_CASE -> "en el estuche"
        BudPlace.OUT -> "puesto"
        BudPlace.UNKNOWN -> "en posición desconocida"
    }

    private fun buildSummary(count: Int): Notification {
        val text = if (count == 0) "Sin dispositivos conectados" else "$count conectado(s)"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("miBT")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup("mibt")
            .setGroupSummary(true)
            .build()
    }

    // One-shot GATT probe: log services and read the standard battery
    // level (0x2A19) if present. Closed after 15 s.
    // ---
    // Sonda GATT única: registra los servicios y lee el nivel de batería
    // estándar (0x2A19) si existe. Se cierra a los 15 s.
    private fun probeGatt(device: BluetoothDevice) {
        val name = describe(device)
        var gatt: BluetoothGatt? = null
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                LogStore.log(this@BtMonitorService, "GATT", "$name estado=$newState status=$status")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    g.close()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val list = g.services.joinToString(",") { it.uuid.toString() }
                LogStore.log(this@BtMonitorService, "GATT", "$name servicios=[$list]")
                val ch = g.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
                if (ch != null) {
                    @Suppress("DEPRECATION")
                    g.readCharacteristic(ch)
                }
            }

            @Deprecated("Deprecated in Android 13")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: android.bluetooth.BluetoothGattCharacteristic,
                status: Int
            ) {
                @Suppress("DEPRECATION")
                val v = characteristic.value?.firstOrNull()?.toInt()?.and(0xFF)
                LogStore.log(this@BtMonitorService, "GATT", "$name batería estándar=$v status=$status")
                if (v != null && v in 0..100) {
                    vendorBattery[device.address] = v
                    approxBattery.remove(device.address)
                    handler.post { refresh() }
                }
            }
        }
        try {
            gatt = device.connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (t: Throwable) {
            LogStore.log(this, "GATT", "$name error al conectar: ${t.javaClass.simpleName}")
            return
        }
        handler.postDelayed({
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (_: Exception) {
            }
        }, 15000)
    }
}
