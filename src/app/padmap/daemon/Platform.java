package app.padmap.daemon;

import android.content.ComponentName;
import android.view.InputEvent;
import app.padmap.Protocol;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hidden framework APIs, reached by reflection; app_process isn't subject to hidden-API enforcement. */
final class Platform {
    private static final int INJECT_MODE_WAIT_FOR_RESULT = 1;
    private static final long EXEC_TIMEOUT_S = 15;
    private static final int EVIOCGRAB = 0x40044590;
    private static final Pattern TOP_ACTIVITY =
            Pattern.compile("(?:topResumedActivity|mResumedActivity)[=:] ?\\S+ \\S+ ([A-Za-z0-9._]+)/");

    private static Object inputManager;
    private static Method injectInputEvent;

    private Platform() {}

    /** False if the dispatcher rejected the event. */
    static boolean inject(InputEvent event) {
        try {
            if (injectInputEvent == null) bindInputManager();
            return (Boolean) injectInputEvent.invoke(inputManager, event, INJECT_MODE_WAIT_FOR_RESULT);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("injectInputEvent failed", e);
        }
    }

    private static void bindInputManager() throws ReflectiveOperationException {
        Class<?> manager;
        try {
            manager = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException e) {
            manager = Class.forName("android.hardware.input.InputManager");
        }
        inputManager = manager.getMethod("getInstance").invoke(null);
        injectInputEvent = inputManager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
    }

    /** Package of the focused (top resumed) activity, or null. */
    static String focusedPackage() {
        try {
            Object tasks = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            // the interface, not tasks.getClass(): that's the private Stub.Proxy, and invoking a non-public
            // class's method can be refused, silently falling back to a dumpsys per second
            Class<?> service = Class.forName("android.app.IActivityTaskManager");
            Method focused;
            try {
                focused = service.getMethod("getFocusedRootTaskInfo");
            } catch (NoSuchMethodException e) {
                focused = service.getMethod("getFocusedStackInfo"); // Android 11
            }
            Object info = focused.invoke(tasks);
            if (info == null) return null;
            ComponentName top = (ComponentName) info.getClass().getField("topActivity").get(info);
            return top == null ? null : top.getPackageName();
        } catch (ReflectiveOperationException e) {
            return focusedPackageFromDumpsys();
        }
    }

    private static String focusedPackageFromDumpsys() {
        try {
            Matcher matcher = TOP_ACTIVITY.matcher(exec("dumpsys", "activity", "activities"));
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Logical (rotated) size of the default display: {width, height}. */
    static int[] displaySize() {
        try {
            Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Object displays = global.getMethod("getInstance").invoke(null);
            Object info = global.getMethod("getDisplayInfo", int.class).invoke(displays, 0);
            Class<?> type = info.getClass();
            return new int[] {type.getField("logicalWidth").getInt(info), type.getField("logicalHeight").getInt(info)};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("display info unavailable", e);
        }
    }

    /** Exclusive access to an evdev device until its fd is closed, so the focused app doesn't also get the pad. */
    static void grab(FileDescriptor fd) throws IOException {
        try {
            Object os = Class.forName("libcore.io.Libcore").getField("os").get(null);
            for (Method method : os.getClass().getMethods()) {
                if (!method.getName().equals("ioctlInt")) continue;
                Class<?>[] params = method.getParameterTypes();
                // either signature passes the kernel a pointer to an int; EVIOCGRAB only tests it for non-null
                if (params.length == 2) {
                    method.invoke(os, fd, EVIOCGRAB);
                    return;
                }
                if (params.length == 3) {
                    method.invoke(os, fd, EVIOCGRAB, params[2].getConstructor(int.class).newInstance(1));
                    return;
                }
            }
            throw new IOException("no ioctlInt in libcore");
        } catch (ReflectiveOperationException e) {
            throw new IOException("EVIOCGRAB failed", e.getCause() != null ? e.getCause() : e);
        }
    }

    /** Starts (or re-handshakes) the app's overlay service with this daemon's token, in the background. */
    static void startApp(String token, boolean edit) {
        Thread thread = new Thread(() -> {
            try {
                exec("am", "start-foreground-service", "-n", Protocol.SERVICE, "--es", Protocol.EXTRA_TOKEN, token,
                        "--ez", Protocol.EXTRA_EDIT, String.valueOf(edit));
            } catch (IOException e) {
                Log.e("starting the app failed", e);
            }
        }, "padmap-start-app");
        thread.setDaemon(true);
        thread.start();
    }

    static void allowOverlay() {
        try {
            exec("appops", "set", Protocol.PACKAGE, "SYSTEM_ALERT_WINDOW", "allow");
        } catch (IOException e) {
            Log.e("granting the overlay permission failed", e);
        }
    }

    static String exec(String... command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        Thread watchdog = new Thread(() -> {
            try {
                if (!process.waitFor(EXEC_TIMEOUT_S, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException ignored) {
                // exec finished first
            }
        }, "padmap-exec-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        try (InputStream in = process.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n = in.read(buffer); n > 0; n = in.read(buffer)) out.write(buffer, 0, n);
            process.waitFor();
            return out.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            watchdog.interrupt();
        }
    }
}
