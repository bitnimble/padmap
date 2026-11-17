package app.padmap.daemon

import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.InputDevice
import java.util.concurrent.Executors

/** Rumbles the gamepad while it maps an app. Every method returns at once; the work runs on its own thread. */
internal class Rumble {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "padmap-rumble").apply { isDaemon = true }
    }
    private var padName: String? = null
    private var mapping = false
    private var reported = false
    private var motors: VibratorManager? = null

    fun setPad(name: String?) = post {
        if (name == padName) return@post
        stop()
        padName = name
        motors = null
        reported = false
        motors()
    }

    fun setMapping(on: Boolean) = post {
        if (!on) stop()
        mapping = on
    }

    fun vibrate(millis: Long) = post {
        if (!mapping || millis <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@post
        (motors() ?: return@post).vibrate(
            CombinedVibration.createParallel(
                VibrationEffect.createOneShot(minOf(millis, MAX_MS), VibrationEffect.DEFAULT_AMPLITUDE),
            ),
        )
    }

    fun cancel() = post(::stop)

    private fun post(task: () -> Unit) {
        worker.execute {
            try {
                task()
            } catch (e: RuntimeException) {
                Log.e("rumble failed", e)
                motors = null
            }
        }
    }

    private fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) motors?.cancel()
    }

    /** Looked up lazily: Android may list the pad after getevent sees it. */
    private fun motors(): VibratorManager? {
        if (motors != null || padName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return motors
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id)
            if (device == null || device.name != padName) continue
            val candidate = device.vibratorManager
            if (candidate.vibratorIds.isEmpty()) continue
            motors = candidate
            Log.i("rumble: $padName has ${candidate.vibratorIds.size} motor(s)")
            return candidate
        }
        if (!reported) Log.i("rumble: Android lists no $padName with motors")
        reported = true
        return null
    }

    companion object {
        const val MAX_MS = 5000L
    }
}
