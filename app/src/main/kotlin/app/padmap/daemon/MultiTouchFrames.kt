package app.padmap.daemon

import app.padmap.daemon.TouchStep.Cancel
import app.padmap.daemon.TouchStep.Down
import app.padmap.daemon.TouchStep.Move
import app.padmap.daemon.TouchStep.Up
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Encodes gesture steps as Linux multi-touch (protocol B) frames, in slots counted down from [topSlot] so they don't
 * collide with a writer filling slots from 0 up. Every frame selects its slots, so another writer's frames can
 * interleave with these.
 */
internal class MultiTouchFrames(private val topSlot: Int, private val wideTime: Boolean) {
    private var nextTrackingId = FIRST_TRACKING_ID

    /** One write's worth of input_event structs; empty if the step has nothing a slot can carry. */
    fun encode(step: TouchStep, timeNanos: Long): ByteArray {
        val events = mutableListOf<IntArray>()
        when (step) {
            is Down -> {
                val pointer = step.pointers.first { it.id == step.id }
                val slot = slot(pointer.id) ?: return ByteArray(0)
                events += abs(ABS_MT_SLOT, slot)
                events += abs(ABS_MT_TRACKING_ID, nextTrackingId)
                nextTrackingId = if (nextTrackingId == MAX_TRACKING_ID) FIRST_TRACKING_ID else nextTrackingId + 1
                events += position(pointer)
                events += abs(ABS_MT_PRESSURE, PRESSURE)
                events += syn()
            }
            is Move -> {
                for (pointer in step.pointers) {
                    val slot = slot(pointer.id) ?: continue
                    events += abs(ABS_MT_SLOT, slot)
                    events += position(pointer)
                }
                if (events.isEmpty()) return ByteArray(0)
                events += syn()
            }
            is Up -> {
                val slot = slot(step.id) ?: return ByteArray(0)
                events += abs(ABS_MT_SLOT, slot)
                events += abs(ABS_MT_TRACKING_ID, NO_TRACKING_ID)
                events += syn()
            }
            is Cancel -> {
                // a palm lifts as a cancelled pointer, not a tap: a held skill isn't cast
                for (pointer in step.pointers) {
                    val slot = slot(pointer.id) ?: continue
                    events += abs(ABS_MT_SLOT, slot)
                    events += abs(ABS_MT_TOOL_TYPE, MT_TOOL_PALM)
                    events += syn()
                    events += abs(ABS_MT_TOOL_TYPE, MT_TOOL_FINGER)
                    events += abs(ABS_MT_TRACKING_ID, NO_TRACKING_ID)
                    events += syn()
                }
            }
        }
        return pack(events, timeNanos)
    }

    private fun slot(id: Int): Int? = (topSlot - id).takeIf { it >= 0 }

    private fun position(pointer: Pointer): List<IntArray> = listOf(
        abs(ABS_MT_POSITION_X, pointer.x.roundToInt()),
        abs(ABS_MT_POSITION_Y, pointer.y.roundToInt()),
    )

    private fun pack(events: List<IntArray>, timeNanos: Long): ByteArray {
        val size = if (wideTime) 24 else 16
        val buffer = ByteBuffer.allocate(events.size * size).order(ByteOrder.LITTLE_ENDIAN)
        val seconds = timeNanos / 1_000_000_000
        val micros = timeNanos % 1_000_000_000 / 1_000
        for ((type, code, value) in events) {
            if (wideTime) {
                buffer.putLong(seconds).putLong(micros)
            } else {
                buffer.putInt(seconds.toInt()).putInt(micros.toInt())
            }
            buffer.putShort(type.toShort()).putShort(code.toShort()).putInt(value)
        }
        return buffer.array()
    }

    companion object {
        const val EV_SYN = 0
        const val EV_ABS = 3
        const val ABS_MT_SLOT = 0x2f
        const val ABS_MT_POSITION_X = 0x35
        const val ABS_MT_POSITION_Y = 0x36
        const val ABS_MT_TOOL_TYPE = 0x37
        const val ABS_MT_TRACKING_ID = 0x39
        const val ABS_MT_PRESSURE = 0x3a
        const val MT_TOOL_FINGER = 0
        const val MT_TOOL_PALM = 2
        const val NO_TRACKING_ID = -1
        const val PRESSURE = 50
        const val FIRST_TRACKING_ID = 1000
        const val MAX_TRACKING_ID = 65535

        private fun abs(code: Int, value: Int) = intArrayOf(EV_ABS, code, value)

        private fun syn() = intArrayOf(EV_SYN, 0, 0)
    }
}
