package app.padmap.daemon

/** Input devices as `getevent -p` describes them: key codes, and per axis its range and resting value. */
internal object Getevent {
    const val BTN_SOUTH = 0x130
    const val ABS_X = 0x00
    const val ABS_Y = 0x01
    const val ABS_Z = 0x02
    const val ABS_RX = 0x03
    const val ABS_RY = 0x04
    const val ABS_RZ = 0x05
    const val ABS_GAS = 0x09
    const val ABS_BRAKE = 0x0a
    const val ABS_HAT0X = 0x10
    const val ABS_HAT0Y = 0x11

    private val DEVICE = Regex("^add device \\d+: (\\S+)")
    private val NAME = Regex("^\\s+name:\\s+\"(.*)\"")
    private val SECTION = Regex("^\\s+([A-Z]+)\\s*\\(([0-9a-f]{4})\\):(.*)$")
    private val ABS_RANGE = Regex("([0-9a-f]{4})\\s+: value (-?\\d+), min (-?\\d+), max (-?\\d+)")
    private val KEY_CODE = Regex("[0-9a-f]{4}")
    private val WHITESPACE = Regex("\\s+")

    /** An axis's range and its value when getevent read it (at rest, normally). */
    data class Range(val rest: Int, val min: Int, val max: Int) {
        fun centred(value: Int): Double {
            if (max == min) return 0.0
            return ((value - (min + max) / 2.0) / ((max - min) / 2.0)).coerceIn(-1.0, 1.0)
        }

        fun fraction(value: Int): Double {
            if (max == min) return 0.0
            return ((value - min) / (max - min).toDouble()).coerceIn(0.0, 1.0)
        }

        fun restsAtMin(): Boolean = fraction(rest) < 0.25
    }

    /** Where a pad reports its right stick and triggers; a trigger rests at its minimum, a stick at its centre. */
    data class Axes(val rightX: Int, val rightY: Int, val leftTrigger: Int, val rightTrigger: Int) {
        companion object {
            fun of(ranges: Map<Int, Range>): Axes {
                val rx = ranges[ABS_RX] ?: return Axes(ABS_Z, ABS_RZ, ABS_BRAKE, ABS_GAS)
                return if (rx.restsAtMin()) Axes(ABS_Z, ABS_RZ, ABS_RX, ABS_RY) else Axes(ABS_RX, ABS_RY, ABS_Z, ABS_RZ)
            }
        }
    }

    data class Device(val path: String, val name: String, val keys: Set<Int>, val ranges: Map<Int, Range>) {
        fun isGamepad(): Boolean = BTN_SOUTH in keys && ABS_X in ranges && ABS_Y in ranges
    }

    fun parse(output: String): List<Device> {
        val devices = mutableListOf<Device>()
        var path: String? = null
        var name = ""
        var section = ""
        var keys = HashSet<Int>()
        var ranges = HashMap<Int, Range>()
        for (line in output.split("\n")) {
            val device = DEVICE.find(line)
            if (device != null) {
                path?.let { devices += Device(it, name, keys, ranges) }
                path = device.groupValues[1]
                name = ""
                section = ""
                keys = HashSet()
                ranges = HashMap()
                continue
            }
            val named = NAME.find(line)
            if (named != null) {
                name = named.groupValues[1]
                continue
            }
            var body = line
            val header = SECTION.find(line)
            if (header != null) {
                section = header.groupValues[1]
                body = header.groupValues[3]
            } else if (!line.startsWith("        ")) {
                section = ""
            }
            when (section) {
                "KEY" -> for (token in body.trim().split(WHITESPACE)) {
                    if (KEY_CODE.matches(token)) keys += token.toInt(16)
                }
                "ABS" -> ABS_RANGE.find(body)?.groupValues?.let { range ->
                    ranges[range[1].toInt(16)] = Range(range[2].toInt(), range[3].toInt(), range[4].toInt())
                }
            }
        }
        path?.let { devices += Device(it, name, keys, ranges) }
        return devices
    }
}
