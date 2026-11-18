package app.padmap.daemon

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import app.padmap.daemon.TouchStep.Cancel
import app.padmap.daemon.TouchStep.Down
import app.padmap.daemon.TouchStep.Move
import app.padmap.daemon.TouchStep.Up

/**
 * Injects the gesture as its own input device. Android lets one device touch a window at a time, so a real finger
 * cancels it.
 */
internal class InjectedTouches : TouchOutput {
    private var downTime = 0L
    private var lastEventTime = 0L

    override fun send(steps: List<TouchStep>): Boolean {
        for (step in steps) {
            val event = obtain(step)
            try {
                if (!Platform.inject(event)) return false
            } finally {
                event.recycle()
            }
        }
        return true
    }

    private fun obtain(step: TouchStep): MotionEvent {
        val pointers = step.pointers
        val action = when (step) {
            is Down -> pointerAction(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, step.id, pointers)
            is Move -> MotionEvent.ACTION_MOVE
            is Up -> pointerAction(MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, step.id, pointers)
            is Cancel -> MotionEvent.ACTION_CANCEL
        }
        lastEventTime = maxOf(SystemClock.uptimeMillis(), lastEventTime + 1)
        if (action == MotionEvent.ACTION_DOWN) downTime = lastEventTime
        val properties = pointers.map { pointer ->
            MotionEvent.PointerProperties().apply {
                id = pointer.id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coords = pointers.map { pointer ->
            MotionEvent.PointerCoords().apply {
                x = pointer.x
                y = pointer.y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        return MotionEvent.obtain(
            downTime, lastEventTime, action, pointers.size, properties, coords,
            0, 0, 1f, 1f, DEVICE_ID, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
    }

    private fun pointerAction(single: Int, multi: Int, id: Int, pointers: List<Pointer>): Int =
        if (pointers.size == 1) {
            single
        } else {
            multi or (pointers.indexOfFirst { it.id == id } shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }

    private companion object {
        // a device id no real touchscreen has: with the touchscreen's own id, a finger's DOWN would silently merge
        // into our gesture instead of the dispatcher rejecting ours
        const val DEVICE_ID = -1
    }
}
