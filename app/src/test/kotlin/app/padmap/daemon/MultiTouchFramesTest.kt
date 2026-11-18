package app.padmap.daemon

import app.padmap.daemon.MultiTouchFrames.Companion.ABS_MT_POSITION_X
import app.padmap.daemon.MultiTouchFrames.Companion.ABS_MT_POSITION_Y
import app.padmap.daemon.MultiTouchFrames.Companion.ABS_MT_PRESSURE
import app.padmap.daemon.MultiTouchFrames.Companion.ABS_MT_SLOT
import app.padmap.daemon.MultiTouchFrames.Companion.ABS_MT_TOOL_TYPE
import app.padmap.daemon.MultiTouchFrames.Companion.ABS_MT_TRACKING_ID
import app.padmap.daemon.TouchStep.Cancel
import app.padmap.daemon.TouchStep.Down
import app.padmap.daemon.TouchStep.Move
import app.padmap.daemon.TouchStep.Up
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiTouchFramesTest {
    private val stick = Pointer(0, 100.4f, 200.6f)
    private val tap = Pointer(1, 640f, 400f)

    @Test
    fun aDownTakesTheTopSlotWithItsOwnTrackingId() {
        assertEquals(
            listOf(abs(ABS_MT_SLOT, 9), abs(ABS_MT_TRACKING_ID, 1000), abs(ABS_MT_POSITION_X, 100),
                abs(ABS_MT_POSITION_Y, 201), abs(ABS_MT_PRESSURE, 50), SYN),
            events(frames().encode(Down(0, listOf(stick)), 0)),
        )
    }

    @Test
    fun laterTouchesCountDownFromTheTopSlot() {
        val frames = frames()
        frames.encode(Down(0, listOf(stick)), 0)
        val second = events(frames.encode(Down(1, listOf(stick, tap)), 0))
        assertEquals(listOf(abs(ABS_MT_SLOT, 8), abs(ABS_MT_TRACKING_ID, 1001)), second.take(2))
    }

    @Test
    fun aMoveSelectsEachSlotBeforeItsPosition() {
        assertEquals(
            listOf(abs(ABS_MT_SLOT, 9), abs(ABS_MT_POSITION_X, 100), abs(ABS_MT_POSITION_Y, 201),
                abs(ABS_MT_SLOT, 8), abs(ABS_MT_POSITION_X, 640), abs(ABS_MT_POSITION_Y, 400), SYN),
            events(frames().encode(Move(listOf(stick, tap)), 0)),
        )
    }

    @Test
    fun anUpEndsOnlyItsOwnSlot() {
        assertEquals(
            listOf(abs(ABS_MT_SLOT, 8), abs(ABS_MT_TRACKING_ID, -1), SYN),
            events(frames().encode(Up(1, listOf(stick, tap)), 0)),
        )
    }

    @Test
    fun aCancelLiftsEachTouchAsAPalm() {
        assertEquals(
            listOf(abs(ABS_MT_SLOT, 9), abs(ABS_MT_TOOL_TYPE, 2), SYN,
                abs(ABS_MT_TOOL_TYPE, 0), abs(ABS_MT_TRACKING_ID, -1), SYN),
            events(frames().encode(Cancel(listOf(stick)), 0)),
        )
    }

    @Test
    fun aTouchBeyondTheSlotsIsLeftOut() {
        assertEquals(0, frames().encode(Down(10, listOf(Pointer(10, 1f, 1f))), 0).size)
    }

    @Test
    fun eventsCarryTheTimeAsSecondsAndMicroseconds() {
        val bytes = ByteBuffer.wrap(frames().encode(Up(0, listOf(stick)), 12_345_678_901L)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(12L, bytes.getLong(0))
        assertEquals(345_678L, bytes.getLong(8))
        assertEquals(3 * 16, MultiTouchFrames(9, wideTime = false).encode(Up(0, listOf(stick)), 0).size)
    }

    private fun frames() = MultiTouchFrames(9, wideTime = true)

    private fun abs(code: Int, value: Int) = Triple(3, code, value)

    private fun events(frame: ByteArray): List<Triple<Int, Int, Int>> {
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        return (0 until frame.size / 24).map { i ->
            Triple(buffer.getShort(i * 24 + 16).toInt(), buffer.getShort(i * 24 + 18).toInt(), buffer.getInt(i * 24 + 20))
        }
    }

    private companion object {
        val SYN = Triple(0, 0, 0)
    }
}
