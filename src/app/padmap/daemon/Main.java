package app.padmap.daemon;

import android.os.SystemClock;
import app.padmap.layout.Layout;
import app.padmap.layout.LayoutJson;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The privileged half, run from the app's APK as shell (adb) or root:
 * CLASSPATH=<apk> app_process / app.padmap.daemon.Main
 */
public final class Main {
    private static final long WATCH_MS = 1000, APP_RESTART_AFTER_MS = 10_000;

    private Main() {}

    public static void main(String[] args) throws IOException {
        Store store = new Store();
        TouchInjector injector = new TouchInjector();
        Engine engine = new Engine(injector, store, newToken());
        Server server = new Server(engine);
        Runtime.getRuntime().addShutdownHook(new Thread(engine::shutdown));
        Gamepad gamepad = new Gamepad(engine::onPad, engine::onPadConnected);
        engine.setGamepad(gamepad);
        start(injector, "padmap-touch");
        start(gamepad, "padmap-gamepad");
        start(() -> watch(engine, store), "padmap-watch");
        Platform.allowOverlay();
        Platform.startApp(engine.token(), false);
        Log.i("padmap daemon running as uid " + android.os.Process.myUid());
        server.serve();
    }

    private static String newToken() {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        StringBuilder hex = new StringBuilder();
        for (byte b : secret) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static void start(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Follows the focused app and its layout file (picking up saves and hand edits), and keeps the app alive. */
    private static void watch(Engine engine, Store store) {
        String pkg = null;
        long modified = -1;
        Layout layout = Layout.EMPTY;
        long appStartedAt = 0;
        while (true) {
            String focused = Platform.focusedPackage();
            File file = Store.validPackage(focused) ? store.file(focused) : null;
            long fileModified = file == null ? -1 : file.lastModified();
            if (!Objects.equals(focused, pkg) || fileModified != modified) {
                pkg = focused;
                modified = fileModified;
                layout = file == null || fileModified == 0 ? Layout.EMPTY : load(store, focused);
            }
            int[] size = Platform.displaySize();
            engine.onFocus(pkg, layout, size[0], size[1]);
            long now = SystemClock.uptimeMillis();
            if (engine.aloneFor(APP_RESTART_AFTER_MS) && now - appStartedAt >= APP_RESTART_AFTER_MS) {
                Platform.startApp(engine.token(), false);
                appStartedAt = now;
            }
            SystemClock.sleep(WATCH_MS);
        }
    }

    private static Layout load(Store store, String pkg) {
        try {
            Layout layout = LayoutJson.parse(new JSONObject(store.read(pkg)));
            Log.i("layout for " + pkg + ": " + layout.controls().size() + " controls");
            return layout;
        } catch (IOException | JSONException e) {
            Log.e("layout for " + pkg + " unreadable; mapping off", e);
            return Layout.EMPTY;
        }
    }
}
