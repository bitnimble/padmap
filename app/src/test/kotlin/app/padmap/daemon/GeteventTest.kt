package app.padmap.daemon

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeteventTest {
    @Test
    fun parsesOneDeviceWithItsPathAndName() {
        val steam = Getevent.parse(STEAM_PAD)
        assertEquals(listOf("/dev/input/event10"), steam.map { it.path })
        assertEquals("Microsoft X-Box 360 pad 0", steam[0].name)
    }

    @Test
    fun readsKeysAcrossContinuationLines() {
        assertEquals(
            setOf(0x130, 0x131, 0x133, 0x134, 0x136, 0x137, 0x13a, 0x13b, 0x13c, 0x13d, 0x13e),
            Getevent.parse(STEAM_PAD)[0].keys,
        )
    }

    @Test
    fun readsTriggerRange() {
        assertEquals(Getevent.Range(0, 0, 255), Getevent.parse(STEAM_PAD)[0].ranges[0x02])
    }

    @Test
    fun xpadIsAGamepadWithRightStickOnRxRyAndTriggersOnZRz() {
        val pad = Getevent.parse(STEAM_PAD)[0]
        assertTrue(pad.isGamepad())
        assertEquals(Getevent.Axes(0x03, 0x04, 0x02, 0x05), Getevent.Axes.of(pad.ranges))
    }

    @Test
    fun touchscreenIsNotAGamepad() {
        val mixed = Getevent.parse(TOUCHSCREEN_AND_HID_PAD)
        assertEquals(2, mixed.size)
        assertFalse(mixed[0].isGamepad())
        assertTrue(mixed[1].isGamepad())
    }

    @Test
    fun hidPadWithTriggersRestingOnRxRyHasRightStickOnZRz() {
        val pad = Getevent.parse(TOUCHSCREEN_AND_HID_PAD)[1]
        assertEquals(Getevent.Axes(0x02, 0x05, 0x03, 0x04), Getevent.Axes.of(pad.ranges))
    }

    @Test
    fun unsignedStickIsCentredAtMidpoint() {
        val range = Getevent.parse(TOUCHSCREEN_AND_HID_PAD)[1].ranges.getValue(0x00)
        assertTrue(abs(range.centred(128)) < 0.01)
    }

    private companion object {
        val STEAM_PAD = """
            add device 1: /dev/input/event10
              name:     "Microsoft X-Box 360 pad 0"
              events:
                KEY (0001): 0130  0131  0133  0134  0136  0137  013a  013b${" "}
                            013c  013d  013e${" "}
                ABS (0003): 0000  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0001  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0002  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0003  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0004  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0005  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0010  : value 0, min -1, max 1, fuzz 0, flat 0, resolution 0
                            0011  : value 0, min -1, max 1, fuzz 0, flat 0, resolution 0
              input props:
                <none>
        """.trimIndent() + "\n"

        val TOUCHSCREEN_AND_HID_PAD = """
            add device 1: /dev/input/event2
              name:     "touchscreen"
              events:
                KEY (0001): 014a
                ABS (0003): 0035  : value 0, min 0, max 1279, fuzz 0, flat 0, resolution 0
            add device 2: /dev/input/event5
              name:     "Generic HID pad"
              events:
                KEY (0001): 0130  0131
                ABS (0003): 0000  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
                            0001  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
                            0002  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
                            0003  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0004  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0005  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
        """.trimIndent() + "\n"
    }
}
