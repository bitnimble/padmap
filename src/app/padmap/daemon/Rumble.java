package app.padmap.daemon;

import android.os.CombinedVibration;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import android.view.InputDevice;
import java.util.Objects;

/** Rumbles the mapped gamepad through Android's input-device vibrators, only while the pad is mapping an app. */
final class Rumble {
    static final long MAX_MS = 5000;

    private String padName;
    private boolean mapping;
    private VibratorManager motors;

    synchronized void setPad(String name) {
        if (Objects.equals(name, padName)) return;
        cancel();
        padName = name;
        motors = name == null ? null : find(name);
    }

    synchronized void setMapping(boolean mapping) {
        if (!mapping) cancel();
        this.mapping = mapping;
    }

    synchronized void vibrate(long millis) {
        if (!mapping || motors == null || millis <= 0) return;
        motors.vibrate(CombinedVibration.createParallel(
                VibrationEffect.createOneShot(Math.min(millis, MAX_MS), VibrationEffect.DEFAULT_AMPLITUDE)));
    }

    synchronized void cancel() {
        if (motors != null) motors.cancel();
    }

    private static VibratorManager find(String name) {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || !device.getName().equals(name)) continue;
            VibratorManager motors = device.getVibratorManager();
            int count = motors.getVibratorIds().length;
            Log.i("rumble: " + name + " has " + count + " motor(s)");
            return count > 0 ? motors : null;
        }
        Log.i("rumble: Android has no input device named " + name);
        return null;
    }
}
