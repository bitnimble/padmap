package app.padmap.daemon

import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import app.padmap.daemon.Getevent.ABS_HAT0X
import app.padmap.daemon.Getevent.ABS_HAT0Y
import app.padmap.daemon.Getevent.ABS_X
import app.padmap.daemon.Getevent.ABS_Y
import app.padmap.daemon.Getevent.Axes
import app.padmap.daemon.Getevent.Device
import app.padmap.layout.Button
import app.padmap.layout.Mapper
import app.padmap.layout.PadState
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads the first evdev gamepad, rescanning when it goes away; grabbed (hidden from Android) only while [setGrab] asks. */
internal class Gamepad(
    private val listener: (PadState) -> Unit,
    private val connected: (name: String?) -> Unit,
) : Runnable {
    @Volatile
    private var wantGrab = false

    fun setGrab(grab: Boolean) {
        wantGrab = grab
    }

    override fun run() {
        while (true) {
            val device = find()
            if (device == null) {
                SystemClock.sleep(RESCAN_MS)
                continue
            }
            connected(device.name)
            Log.i("gamepad: ${device.name} at ${device.path}")
            try {
                read(device)
            } catch (e: IOException) {
                Log.i("gamepad ${device.path} lost: ${e.message}")
            } catch (e: ErrnoException) {
                Log.i("gamepad ${device.path} lost: ${e.message}")
            } catch (e: RuntimeException) {
                Log.e("reading the gamepad failed; reopening it", e)
            }
            connected(null)
            listener(PadState.NEUTRAL)
            SystemClock.sleep(RESCAN_MS)
        }
    }

    private fun find(): Device? = try {
        Getevent.parse(Platform.exec("getevent", "-p")).firstOrNull { it.isGamepad() }
    } catch (e: IOException) {
        Log.i("getevent -p failed: ${e.message}")
        null
    }

    private fun read(device: Device) {
        val axes = Axes.of(device.ranges)
        val keys = HashSet<String>()
        val values = HashMap<Int, Int>()
        val buffer = ByteArray(EVENT_SIZE * 64)
        val events = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        var fd: FileDescriptor? = Os.open(device.path, OsConstants.O_RDONLY, 0)
        var grabbed = false
        var grabFailed = false
        var waitReported = false
        var dropping = false
        var last = PadState.NEUTRAL
        try {
            while (true) {
                if (wantGrab && !grabbed && !atRest(last) && !waitReported) {
                    Log.i("gamepad: waiting for buttons and sticks to rest before taking it from Android")
                    waitReported = true
                }
                // grabbing mid-press would leave Android holding that button or axis forever
                if (wantGrab && !grabbed && !grabFailed && atRest(last)) {
                    try {
                        Platform.grab(fd)
                        grabbed = true
                    } catch (e: IOException) {
                        Log.e("can't grab the gamepad; Android will see it too", e)
                        grabFailed = true
                    }
                } else if (!wantGrab && grabbed) {
                    // EVIOCGRAB can't be released through libcore (it always passes a non-null pointer): reopen
                    val grabbedFd = fd
                    fd = null
                    Os.close(grabbedFd)
                    fd = Os.open(device.path, OsConstants.O_RDONLY, 0)
                    grabbed = false
                }
                val poll = StructPollfd()
                poll.fd = fd
                poll.events = OsConstants.POLLIN.toShort()
                if (Os.poll(arrayOf(poll), POLL_MS) == 0) continue
                if ((poll.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP)) != 0) {
                    throw IOException("device gone")
                }
                val length = Os.read(fd, buffer, 0, buffer.size)
                if (length <= 0) throw IOException("end of stream")
                for (offset in 0..length - EVENT_SIZE step EVENT_SIZE) {
                    val type = events.getShort(offset + EVENT_SIZE - 8).toInt() and 0xffff
                    val code = events.getShort(offset + EVENT_SIZE - 6).toInt() and 0xffff
                    val value = events.getInt(offset + EVENT_SIZE - 4)
                    when {
                        type == EV_SYN && code == SYN_DROPPED -> dropping = true
                        // after an overrun the frame is partial; resume at the next complete one
                        dropping -> dropping = !(type == EV_SYN && code == SYN_REPORT)
                        type == EV_KEY -> {
                            val button = BUTTONS[code] ?: "KEY_%X".format(code)
                            if (value == 0) keys -= button else keys += button
                        }
                        type == EV_ABS -> values[code] = value
                        type == EV_SYN && code == SYN_REPORT -> {
                            last = state(device, axes, keys, values)
                            listener(last)
                        }
                    }
                }
            }
        } finally {
            fd?.let(Os::close)
        }
    }

    private fun atRest(state: PadState): Boolean = state.buttons.isEmpty() &&
        state.left.magnitude() < Mapper.DEADZONE && state.right.magnitude() < Mapper.DEADZONE

    private fun state(device: Device, axes: Axes, keys: Set<String>, values: Map<Int, Int>): PadState {
        val buttons = HashSet(keys)
        if (fraction(device, values, axes.leftTrigger) > 0.5) buttons += "LT"
        if (fraction(device, values, axes.rightTrigger) > 0.5) buttons += "RT"
        val hatX = centred(device, values, ABS_HAT0X)
        val hatY = centred(device, values, ABS_HAT0Y)
        if (hatX < -0.5) buttons += "LEFT"
        if (hatX > 0.5) buttons += "RIGHT"
        if (hatY < -0.5) buttons += "UP"
        if (hatY > 0.5) buttons += "DOWN"
        return PadState(
            buttons,
            PadState.Stick(centred(device, values, ABS_X), centred(device, values, ABS_Y)),
            PadState.Stick(centred(device, values, axes.rightX), centred(device, values, axes.rightY)),
        )
    }

    private fun centred(device: Device, values: Map<Int, Int>, code: Int): Double {
        val range = device.ranges[code] ?: return 0.0
        return range.centred(values[code] ?: range.rest)
    }

    private fun fraction(device: Device, values: Map<Int, Int>, code: Int): Double {
        val range = device.ranges[code] ?: return 0.0
        return range.fraction(values[code] ?: range.rest)
    }

    private companion object {
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val SYN_REPORT = 0
        const val SYN_DROPPED = 3
        val EVENT_SIZE = if (Process.is64Bit()) 24 else 16
        const val RESCAN_MS = 2000L
        const val POLL_MS = 100
        val BUTTONS = Button.byCode()
    }
}
