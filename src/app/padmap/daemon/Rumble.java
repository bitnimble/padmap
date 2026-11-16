package app.padmap.daemon;

import android.os.Build;
import android.os.CombinedVibration;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import android.view.InputDevice;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rumbles the gamepad while it maps an app. Every method returns at once; the work runs on its own thread. */
final class Rumble {
    static final long MAX_MS = 5000;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "padmap-rumble");
        thread.setDaemon(true);
        return thread;
    });
    private String padName;
    private boolean mapping, reported;
    private VibratorManager motors;

    void setPad(String name) {
        post(() -> {
            if (Objects.equals(name, padName)) return;
            stop();
            padName = name;
            motors = null;
            reported = false;
            motors();
        });
    }

    void setMapping(boolean on) {
        post(() -> {
            if (!on) stop();
            mapping = on;
        });
    }

    void vibrate(long millis) {
        post(() -> {
            if (!mapping || millis <= 0 || motors() == null) return;
            motors.vibrate(CombinedVibration.createParallel(
                    VibrationEffect.createOneShot(Math.min(millis, MAX_MS), VibrationEffect.DEFAULT_AMPLITUDE)));
        });
    }

    void cancel() {
        post(this::stop);
    }

    private void post(Runnable task) {
        worker.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                Log.e("rumble failed", e);
                motors = null;
            }
        });
    }

    private void stop() {
        if (motors != null) motors.cancel();
    }

    /** Looked up lazily: Android may list the pad after getevent sees it. */
    private VibratorManager motors() {
        if (motors != null || padName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return motors;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || !device.getName().equals(padName)) continue;
            VibratorManager candidate = device.getVibratorManager();
            if (candidate.getVibratorIds().length == 0) continue;
            motors = candidate;
            Log.i("rumble: " + padName + " has " + candidate.getVibratorIds().length + " motor(s)");
            return motors;
        }
        if (!reported) Log.i("rumble: Android lists no " + padName + " with motors");
        reported = true;
        return null;
    }
}
