package app.padmap.daemon

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.io.InterruptedIOException

/**
 * Writes the gesture into Waydroid's touchscreen pipe, which Android reads as the screen's own multi-touch device:
 * the pad's touches and real fingers then reach the game as one gesture, as on a phone.
 */
internal class WaylandTouch private constructor(private val fd: FileDescriptor) : TouchOutput {
    // input_event's layout is the reader's (system_server), not this process's
    private val frames = MultiTouchFrames(TOP_SLOT, Build.SUPPORTED_64_BIT_ABIS.isNotEmpty())

    override fun send(steps: List<TouchStep>): Boolean {
        for (step in steps) {
            val frame = frames.encode(step, System.nanoTime())
            if (frame.isEmpty()) continue
            try {
                // one write per frame: the pipe keeps it whole between the display's own frames
                if (Os.write(fd, frame, 0, frame.size) != frame.size) return false
            } catch (e: ErrnoException) {
                Log.e("writing to $PATH failed", e)
                return false
            } catch (e: InterruptedIOException) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val PATH = "/dev/input/wl_touch_events"

        // Waydroid's EventHub gives wayland_touch slots 0-9; the display fills them from 0
        private const val TOP_SLOT = 9

        /** Null unless Android reads a Waydroid touchscreen pipe this process can write to. */
        fun open(): WaylandTouch? = try {
            WaylandTouch(Os.open(PATH, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC, 0))
        } catch (e: ErrnoException) {
            null
        }
    }
}
