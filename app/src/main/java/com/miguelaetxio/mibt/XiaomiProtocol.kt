package com.miguelaetxio.mibt

// Battery of a single element (left bud, right bud or case).
// ---
// Batería de un elemento (auricular izquierdo, derecho o estuche).
data class XiaomiCell(val percent: Int, val charging: Boolean)

// Battery list from the 7-element +XIAOMI form; null cell = no data (255).
// ---
// Lista de batería de la forma +XIAOMI de 7 elementos; celda null = sin dato (255).
data class XiaomiBattery(
    val left: XiaomiCell?,
    val right: XiaomiCell?,
    val case: XiaomiCell?
)

enum class BudPlace { IN_CASE, OUT, UNKNOWN }

// Where each bud is, from the FF010201010400020A{XX}FF frame.
// ---
// Dónde está cada auricular, según la trama FF010201010400020A{XX}FF.
data class XiaomiPosition(val left: BudPlace, val right: BudPlace)

object XiaomiProtocol {

    private const val POSITION_PREFIX = "FF010201010400020A"
    private const val POSITION_SUFFIX = "FF"

    // Form [1,1,X,L,R,C,D]: bit 7 = charging, low 7 bits = percent, 255 = no data.
    // X and D are not decoded and are ignored. Any other shape returns null.
    // ---
    // Forma [1,1,X,L,R,C,D]: bit 7 = cargando, 7 bits bajos = porcentaje, 255 = sin dato.
    // X y D no están descifrados y se ignoran. Cualquier otra forma devuelve null.
    fun parseBattery(args: Array<*>?): XiaomiBattery? {
        if (args == null || args.size != 7) return null
        val values = args.map { it?.toString()?.trim()?.toIntOrNull() ?: return null }
        if (values[0] != 1 || values[1] != 1) return null
        if (values.subList(3, 6).any { it !in 0..255 }) return null
        return XiaomiBattery(
            left = cell(values[3]),
            right = cell(values[4]),
            case = cell(values[5])
        )
    }

    // Single-element frame FF010201010400020A{XX}FF. Bits of XX:
    // 0 right in case, 1 left in case, 2 right out, 3 left out.
    // ---
    // Trama de un elemento FF010201010400020A{XX}FF. Bits de XX:
    // 0 derecho en estuche, 1 izquierdo en estuche, 2 derecho fuera, 3 izquierdo fuera.
    fun parsePosition(args: Array<*>?): XiaomiPosition? {
        if (args == null || args.size != 1) return null
        val frame = args[0]?.toString()?.replace(" ", "")?.uppercase() ?: return null
        if (frame.length != POSITION_PREFIX.length + 2 + POSITION_SUFFIX.length) return null
        if (!frame.startsWith(POSITION_PREFIX) || !frame.endsWith(POSITION_SUFFIX)) return null
        val flags = frame.substring(POSITION_PREFIX.length, POSITION_PREFIX.length + 2)
            .toIntOrNull(16) ?: return null
        return XiaomiPosition(
            left = place(inCase = flags and 0x02 != 0, out = flags and 0x08 != 0),
            right = place(inCase = flags and 0x01 != 0, out = flags and 0x04 != 0)
        )
    }

    // Raw byte of a cell in the protocol's own encoding (255 = no data); used to
    // persist the last reading and to rebuild it with cell().
    // ---
    // Byte crudo de una celda con la codificación del propio protocolo (255 = sin
    // dato); se usa para persistir la última lectura y reconstruirla con cell().
    fun toRaw(cell: XiaomiCell?): Int =
        if (cell == null) 255 else cell.percent or (if (cell.charging) 0x80 else 0)

    fun cell(raw: Int): XiaomiCell? {
        if (raw == 255) return null
        val percent = raw and 0x7F
        if (percent > 100) return null
        return XiaomiCell(percent, raw and 0x80 != 0)
    }

    private fun place(inCase: Boolean, out: Boolean): BudPlace = when {
        inCase && !out -> BudPlace.IN_CASE
        out && !inCase -> BudPlace.OUT
        else -> BudPlace.UNKNOWN
    }
}
