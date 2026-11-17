package app.padmap.daemon

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import app.padmap.layout.Mapper.Contact

/**
 * Plays the mapper's desired contacts out as one multi-touch gesture stream on its own clock: each touch lands at
 * its anchor, holds for a couple of frames, then ramps to its target, so frame-sampling games (Unity) see where a
 * drag started; taps are held long enough to be seen.
 */
internal class TouchInjector : Runnable {
    private class Touch(val id: Int, val landedAt: Long, val anchorX: Float, val anchorY: Float) {
        var targetX = anchorX
        var targetY = anchorY
        var x = anchorX
        var y = anchorY
        var movedAt = 0L
        var wanted = true

        fun ramped(now: Long): Boolean =
            now - landedAt >= LAND_MS + RAMP_MS || (anchorX == targetX && anchorY == targetY)
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val monitor = this as Object
    // held across building and injecting a batch, so a cancel can't land between a tick's events; taken before this
    private val injecting = Any()
    private val touches = LinkedHashMap<Int, Touch>()
    private var target: Map<Int, Contact> = emptyMap()
    private var cancelPending = false
    private var width = 0
    private var height = 0
    private var downTime = 0L
    private var lastEventTime = 0L

    /** Contacts are normalized; width and height are the display's logical size. */
    @Synchronized
    fun setTarget(contacts: List<Contact>, width: Int, height: Int) {
        target = contacts.associateByTo(HashMap()) { it.control }
        this.width = width
        this.height = height
        monitor.notifyAll()
    }

    /** Abandons every touch without lifting it, so a held skill isn't cast. Returns at once; the clock sends it. */
    @Synchronized
    fun cancel() {
        target = emptyMap()
        cancelPending = touches.isNotEmpty()
        monitor.notifyAll()
    }

    /** [cancel], sent before returning. */
    fun cancelNow() {
        synchronized(injecting) {
            val events = synchronized(this) {
                target = emptyMap()
                takeCancel()
            }
            injectQuietly(events)
        }
    }

    override fun run() {
        while (true) {
            synchronized(this) {
                try {
                    while (target.isEmpty() && touches.isEmpty()) monitor.wait()
                } catch (e: InterruptedException) {
                    return
                }
            }
            var pause = TICK_MS
            synchronized(injecting) {
                try {
                    val events = synchronized(this) {
                        if (cancelPending) takeCancel() else tick(SystemClock.uptimeMillis())
                    }
                    if (!inject(events)) dropGesture()
                } catch (e: RuntimeException) {
                    Log.e("injection failed; dropping the gesture", e)
                    dropGesture()
                    pause = ERROR_BACKOFF_MS
                }
            }
            SystemClock.sleep(pause)
        }
    }

    private fun takeCancel(): List<MotionEvent> {
        cancelPending = false
        if (touches.isEmpty()) return emptyList()
        val event = obtain(MotionEvent.ACTION_CANCEL)
        touches.clear()
        return listOf(event)
    }

    /** The dispatcher rejected us (a real finger took the screen) or injection broke: wait for the next input. */
    private fun dropGesture() {
        val events = synchronized(this) {
            target = emptyMap()
            takeCancel()
        }
        injectQuietly(events)
    }

    private fun tick(now: Long): List<MotionEvent> {
        val events = mutableListOf<MotionEvent>()
        for ((control, touch) in touches) {
            // once released, a touch only lifts: a quick re-press must land a new touch, not extend this one
            if (!touch.wanted) continue
            val contact = target[control]
            touch.wanted = contact != null
            if (contact == null) continue
            touch.targetX = (contact.x * width).toFloat()
            touch.targetY = (contact.y * height).toFloat()
        }
        for (control in touches.keys.toList()) {
            val touch = touches.getValue(control)
            // the last move goes out a frame before the lift, so an aimed skill is released where it was aimed
            if (!touch.wanted && touch.ramped(now) && now - touch.landedAt >= MIN_HOLD_MS &&
                now - touch.movedAt >= TICK_MS
            ) {
                events += lift(control)
            }
        }
        for ((control, contact) in target) {
            if (control !in touches) events += land(control, contact, now)
        }
        var moved = false
        for (touch in touches.values) {
            val progress = ((now - touch.landedAt - LAND_MS) / RAMP_MS.toFloat()).coerceIn(0f, 1f)
            val x = touch.anchorX + (touch.targetX - touch.anchorX) * progress
            val y = touch.anchorY + (touch.targetY - touch.anchorY) * progress
            if (x == touch.x && y == touch.y) continue
            touch.x = x
            touch.y = y
            touch.movedAt = now
            moved = true
        }
        if (moved) events += obtain(MotionEvent.ACTION_MOVE)
        return events
    }

    private fun land(control: Int, contact: Contact, now: Long): MotionEvent {
        if (touches.isEmpty()) downTime = now
        val touch = Touch(freeId(), now, (contact.anchorX * width).toFloat(), (contact.anchorY * height).toFloat())
        touch.targetX = (contact.x * width).toFloat()
        touch.targetY = (contact.y * height).toFloat()
        touches[control] = touch
        val index = touches.size - 1
        return obtain(
            if (index == 0) {
                MotionEvent.ACTION_DOWN
            } else {
                MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            },
        )
    }

    private fun lift(control: Int): MotionEvent {
        val index = touches.keys.indexOf(control)
        val event = obtain(
            if (touches.size == 1) {
                MotionEvent.ACTION_UP
            } else {
                MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            },
        )
        touches.remove(control)
        return event
    }

    private fun freeId(): Int = generateSequence(0) { it + 1 }.first { id -> touches.values.none { it.id == id } }

    private fun obtain(action: Int): MotionEvent {
        val properties = touches.values.map { touch ->
            MotionEvent.PointerProperties().apply {
                id = touch.id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coords = touches.values.map { touch ->
            MotionEvent.PointerCoords().apply {
                x = touch.x
                y = touch.y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        lastEventTime = maxOf(SystemClock.uptimeMillis(), lastEventTime + 1)
        return MotionEvent.obtain(
            downTime, lastEventTime, action, touches.size, properties, coords,
            0, 0, 1f, 1f, DEVICE_ID, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
    }

    private companion object {
        const val TICK_MS = 8L
        const val LAND_MS = 32L
        const val RAMP_MS = 40L
        const val MIN_HOLD_MS = 50L
        const val ERROR_BACKOFF_MS = 1000L
        // a device id no real touchscreen has: with the touchscreen's own id, a finger's DOWN would silently merge
        // into our gesture instead of the dispatcher rejecting ours
        const val DEVICE_ID = -1

        fun injectQuietly(events: List<MotionEvent>) {
            try {
                inject(events)
            } catch (e: RuntimeException) {
                Log.e("cancelling the gesture failed", e)
            }
        }

        /** False as soon as the dispatcher rejects one; the rest are dropped. */
        fun inject(events: List<MotionEvent>): Boolean {
            try {
                return events.all(Platform::inject)
            } finally {
                events.forEach(MotionEvent::recycle)
            }
        }
    }
}
