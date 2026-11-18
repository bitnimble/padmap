package app.padmap.daemon

import android.os.SystemClock
import app.padmap.daemon.TouchStep.Cancel
import app.padmap.daemon.TouchStep.Down
import app.padmap.daemon.TouchStep.Move
import app.padmap.daemon.TouchStep.Up
import app.padmap.layout.Mapper.Contact

/**
 * Plays the mapper's desired contacts out as one multi-touch gesture on its own clock: each touch lands at its
 * anchor, holds for a couple of frames, then ramps to its target, so frame-sampling games (Unity) see where a drag
 * started; taps are held long enough to be seen.
 */
internal class TouchInjector(private val output: TouchOutput) : Runnable {
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
    // held across building and sending a batch, so a cancel can't land between a tick's steps; taken before this
    private val sending = Any()
    private val touches = LinkedHashMap<Int, Touch>()
    private var target: Map<Int, Contact> = emptyMap()
    private var cancelPending = false
    private var width = 0
    private var height = 0

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
        synchronized(sending) {
            val steps = synchronized(this) {
                target = emptyMap()
                takeCancel()
            }
            sendQuietly(steps)
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
            synchronized(sending) {
                try {
                    val steps = synchronized(this) {
                        if (cancelPending) takeCancel() else tick(SystemClock.uptimeMillis())
                    }
                    if (!output.send(steps)) dropGesture()
                } catch (e: RuntimeException) {
                    Log.e("sending touches failed; dropping the gesture", e)
                    dropGesture()
                    pause = ERROR_BACKOFF_MS
                }
            }
            SystemClock.sleep(pause)
        }
    }

    private fun takeCancel(): List<TouchStep> {
        cancelPending = false
        if (touches.isEmpty()) return emptyList()
        val step = Cancel(pointers())
        touches.clear()
        return listOf(step)
    }

    /** The dispatcher gave the screen to a real finger, or sending broke: wait for the next input. */
    private fun dropGesture() {
        val steps = synchronized(this) {
            target = emptyMap()
            takeCancel()
        }
        sendQuietly(steps)
    }

    private fun sendQuietly(steps: List<TouchStep>) {
        try {
            output.send(steps)
        } catch (e: RuntimeException) {
            Log.e("cancelling the gesture failed", e)
        }
    }

    private fun tick(now: Long): List<TouchStep> {
        val steps = mutableListOf<TouchStep>()
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
                steps += Up(touch.id, pointers())
                touches.remove(control)
            }
        }
        for ((control, contact) in target) {
            if (control !in touches) steps += land(control, contact, now)
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
        if (moved) steps += Move(pointers())
        return steps
    }

    private fun land(control: Int, contact: Contact, now: Long): TouchStep {
        val touch = Touch(freeId(), now, (contact.anchorX * width).toFloat(), (contact.anchorY * height).toFloat())
        touch.targetX = (contact.x * width).toFloat()
        touch.targetY = (contact.y * height).toFloat()
        touches[control] = touch
        return Down(touch.id, pointers())
    }

    private fun freeId(): Int = generateSequence(0) { it + 1 }.first { id -> touches.values.none { it.id == id } }

    private fun pointers(): List<Pointer> = touches.values.map { Pointer(it.id, it.x, it.y) }

    private companion object {
        const val TICK_MS = 8L
        const val LAND_MS = 32L
        const val RAMP_MS = 40L
        const val MIN_HOLD_MS = 50L
        const val ERROR_BACKOFF_MS = 1000L
    }
}
