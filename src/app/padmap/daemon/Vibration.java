package app.padmap.daemon;

import android.content.AttributionSource;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.IBinder;
import android.os.IVibratorManagerService;
import android.os.IVibratorStateListener;
import android.os.PermissionEnforcer;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.VibrationAttributes;
import android.os.VibratorInfo;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Forwards the focused app's vibrations to the gamepad. With a real vibrator, its on/off state is mirrored; without
 * one (Android in a container), Padmap registers a stand-in vibrator service that apps vibrate instead.
 */
final class Vibration {
    private static final String SERVICE = "vibrator_manager";
    private static final long CAP_AMPLITUDE_CONTROL = 4;
    private static final long TICK_MS = 30;

    private static IVibratorStateListener mirror;

    private Vibration() {}

    static void start(Rumble rumble) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            IBinder real = ServiceManager.checkService(SERVICE);
            if (real == null) {
                standIn(rumble);
            } else {
                mirror(IVibratorManagerService.Stub.asInterface(real), rumble);
            }
        } catch (RuntimeException | RemoteException | LinkageError e) {
            Log.e("vibration forwarding unavailable", e);
        }
    }

    private static void standIn(Rumble rumble) {
        for (Method method : IVibratorManagerService.class.getMethods()) {
            try {
                if (!Modifier.isAbstract(StandIn.class.getMethod(method.getName(), method.getParameterTypes())
                        .getModifiers())) continue;
            } catch (NoSuchMethodException e) {
                // falls through: this Android's vibrator interface differs from the one StandIn implements
            }
            Log.i("vibration: no vibrator, and this Android's vibrator service has an unknown " + method.getName());
            return;
        }
        ServiceManager.addService(SERVICE, new StandIn(rumble));
        Log.i("vibration: no vibrator; standing in for one, forwarding to the gamepad");
    }

    private static void mirror(IVibratorManagerService service, Rumble rumble) throws RemoteException {
        mirror = new IVibratorStateListener.Stub() {
            @Override
            public void onVibrating(boolean vibrating) {
                if (vibrating) rumble.vibrate(Rumble.MAX_MS);
                else rumble.cancel();
            }
        };
        for (int id : service.getVibratorIds()) service.registerVibratorStateListener(id, mirror);
        Log.i("vibration: mirroring the device's vibrator to the gamepad");
    }

    private static final class StandIn extends IVibratorManagerService.Stub {
        private final Rumble rumble;

        StandIn(Rumble rumble) {
            super(new GrantAll());
            this.rumble = rumble;
        }

        @Override
        public int[] getVibratorIds() {
            return new int[] {0};
        }

        @Override
        public VibratorInfo getVibratorInfo(int vibratorId) {
            return new VibratorInfo.Builder(vibratorId).setCapabilities(CAP_AMPLITUDE_CONTROL).build();
        }

        @Override
        public boolean isVibrating(int vibratorId) {
            return false;
        }

        @Override
        public boolean registerVibratorStateListener(int vibratorId, IVibratorStateListener listener) {
            return false;
        }

        @Override
        public boolean unregisterVibratorStateListener(int vibratorId, IVibratorStateListener listener) {
            return false;
        }

        @Override
        public boolean setAlwaysOnEffect(int uid, String opPkg, int alwaysOnId, CombinedVibration vibration,
                VibrationAttributes attributes) {
            return false;
        }

        @Override
        public void vibrate(int uid, int deviceId, String opPkg, CombinedVibration vibration,
                VibrationAttributes attributes, String reason, IBinder token) {
            rumble.vibrate(millis(vibration));
        }

        @Override
        public void cancelVibrate(int usageFilter, IBinder token) {
            rumble.cancel();
        }

        @Override
        public void performHapticFeedback(int uid, int deviceId, String opPkg, int constant, boolean always,
                String reason, boolean fromIme) {}

        private static long millis(CombinedVibration vibration) {
            try {
                long millis = (Long) CombinedVibration.class.getMethod("getDuration").invoke(vibration);
                if (millis == Long.MAX_VALUE) return Rumble.MAX_MS; // repeating: runs until cancelled
                return millis < 0 ? TICK_MS : millis; // -1: unknown length, e.g. a predefined click
            } catch (ReflectiveOperationException e) {
                return TICK_MS;
            }
        }
    }

    /** The real service checks VIBRATE; apps here already hold it, and app_process has no Context to check with. */
    private static final class GrantAll extends PermissionEnforcer {
        @Override
        public void enforcePermission(String permission, int pid, int uid) {}

        @Override
        public void enforcePermission(String permission, AttributionSource source) {}

        @Override
        public void enforcePermissionAllOf(String[] permissions, int pid, int uid) {}

        @Override
        public void enforcePermissionAllOf(String[] permissions, AttributionSource source) {}

        @Override
        public void enforcePermissionAnyOf(String[] permissions, int pid, int uid) {}

        @Override
        public void enforcePermissionAnyOf(String[] permissions, AttributionSource source) {}
    }
}
