package app.padmap.daemon

import android.os.Build
import android.os.CombinedVibration
import android.os.IBinder
import android.os.IVibratorManagerService
import android.os.IVibratorStateListener
import android.os.PermissionEnforcer
import android.os.RemoteException
import android.os.ServiceManager
import android.os.VibrationAttributes
import android.os.VibratorInfo
import androidx.annotation.RequiresApi
import java.lang.reflect.Modifier

internal object Vibration {
    private const val SERVICE = "vibrator_manager"
    private const val CAP_AMPLITUDE_CONTROL = 4L
    private const val TICK_MS = 30L

    fun start(rumble: Rumble) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val real = ServiceManager.checkService(SERVICE)
            if (real == null) standIn(rumble) else mirror(IVibratorManagerService.Stub.asInterface(real), rumble)
        } catch (e: RuntimeException) {
            Log.e("vibration forwarding unavailable", e)
        } catch (e: RemoteException) {
            Log.e("vibration forwarding unavailable", e)
        } catch (e: LinkageError) {
            Log.e("vibration forwarding unavailable", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun standIn(rumble: Rumble) {
        val unknown = unimplemented()
        if (unknown != null) {
            Log.i("vibration: no vibrator, and this Android's vibrator service has an unknown $unknown")
            return
        }
        ServiceManager.addService(SERVICE, StandIn(rumble))
        Log.i(
            if (ServiceManager.checkService(SERVICE) != null) {
                "vibration: no vibrator; standing in for one, forwarding to the gamepad"
            } else {
                "vibration: no vibrator, and registering a stand-in was refused"
            },
        )
    }

    private fun unimplemented(): String? = IVibratorManagerService::class.java.methods.firstOrNull { method ->
        try {
            Modifier.isAbstract(StandIn::class.java.getMethod(method.name, *method.parameterTypes).modifiers)
        } catch (e: NoSuchMethodException) {
            true
        }
    }?.name

    @RequiresApi(Build.VERSION_CODES.S)
    private fun mirror(service: IVibratorManagerService, rumble: Rumble) {
        val listener = object : IVibratorStateListener.Stub() {
            override fun onVibrating(vibrating: Boolean) {
                if (vibrating) rumble.vibrate(Rumble.MAX_MS) else rumble.cancel()
            }
        }
        for (id in service.vibratorIds) {
            if (!service.registerVibratorStateListener(id, listener)) {
                Log.i("vibration: the device's vibrator $id refused a state listener")
                return
            }
        }
        Log.i("vibration: mirroring the device's vibrator to the gamepad")
    }

    // binder hands these straight to us: nullable params, so a null from a caller can't trip Kotlin's null checks
    @RequiresApi(Build.VERSION_CODES.S)
    private class StandIn(
        private val rumble: Rumble,
    ) : IVibratorManagerService.Stub(
        // the no-arg Stub() needs a system Context; only *_enforcePermission() reads this, and StandIn calls none
        object : PermissionEnforcer() {},
    ) {
        override fun getVibratorIds(): IntArray = intArrayOf(0)

        override fun getVibratorInfo(vibratorId: Int): VibratorInfo =
            VibratorInfo.Builder(vibratorId).setCapabilities(CAP_AMPLITUDE_CONTROL).build()

        override fun isVibrating(vibratorId: Int): Boolean = false

        override fun registerVibratorStateListener(vibratorId: Int, listener: IVibratorStateListener?): Boolean = false

        override fun unregisterVibratorStateListener(vibratorId: Int, listener: IVibratorStateListener?): Boolean = false

        override fun setAlwaysOnEffect(
            uid: Int,
            opPkg: String?,
            alwaysOnId: Int,
            vibration: CombinedVibration?,
            attributes: VibrationAttributes?,
        ): Boolean = false

        override fun vibrate(
            uid: Int,
            deviceId: Int,
            opPkg: String?,
            vibration: CombinedVibration?,
            attributes: VibrationAttributes?,
            reason: String?,
            token: IBinder?,
        ) {
            rumble.vibrate(millis(vibration))
        }

        override fun cancelVibrate(usageFilter: Int, token: IBinder?) {
            rumble.cancel()
        }

        override fun performHapticFeedback(
            uid: Int,
            deviceId: Int,
            opPkg: String?,
            constant: Int,
            always: Boolean,
            reason: String?,
            fromIme: Boolean,
        ) {}

        private fun millis(vibration: CombinedVibration?): Long = try {
            val millis = CombinedVibration::class.java.getMethod("getDuration").invoke(vibration) as Long
            when {
                millis == Long.MAX_VALUE -> Rumble.MAX_MS // repeating: runs until cancelled
                millis < 0 -> TICK_MS // -1: unknown length, e.g. a predefined click
                else -> millis
            }
        } catch (e: ReflectiveOperationException) {
            TICK_MS
        }
    }
}
