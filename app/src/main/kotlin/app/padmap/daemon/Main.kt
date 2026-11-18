package app.padmap.daemon

import android.os.Process
import android.os.SystemClock
import app.padmap.layout.Layout
import app.padmap.layout.LayoutJson
import java.io.IOException
import java.security.SecureRandom
import kotlin.concurrent.thread
import org.json.JSONException
import org.json.JSONObject

/**
 * The privileged half, run from the app's APK as shell (adb) or root:
 * CLASSPATH=<apk> app_process / app.padmap.daemon.Main
 */
object Main {
    private const val WATCH_MS = 1000L
    private const val APP_RESTART_AFTER_MS = 10_000L

    @JvmStatic
    fun main(args: Array<String>) {
        val store = Store()
        val pipe = WaylandTouch.open()
        Log.i(if (pipe != null) "touches: written to the touchscreen's pipe, merged with fingers" else "touches: injected")
        val injector = TouchInjector(pipe ?: InjectedTouches())
        val rumble = Rumble()
        val engine = Engine(injector, rumble, store, newToken())
        val server = Server(engine)
        Runtime.getRuntime().addShutdownHook(Thread(engine::shutdown))
        val gamepad = Gamepad(engine::onPad, engine::onPadConnected)
        engine.setGamepad(gamepad)
        thread(isDaemon = true, name = "padmap-touch") { injector.run() }
        thread(isDaemon = true, name = "padmap-gamepad") { gamepad.run() }
        thread(isDaemon = true, name = "padmap-watch") { watch(engine, store) }
        Vibration.start(rumble)
        Platform.grantPermissions()
        Platform.startApp(engine.token, false)
        Log.i("padmap daemon running as uid ${Process.myUid()}")
        server.serve()
    }

    private fun newToken(): String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun watch(engine: Engine, store: Store) {
        val watched = Watched()
        while (true) {
            try {
                watched.poll(engine, store)
            } catch (e: RuntimeException) {
                Log.e("watching the focused app failed", e)
            }
            SystemClock.sleep(WATCH_MS)
        }
    }

    private class Watched {
        private var pkg: String? = null
        private var modified = -1L
        private var layout = Layout.EMPTY
        private var appStartedAt = 0L
        private var settingsModified = -1L

        fun poll(engine: Engine, store: Store) {
            val modified = store.settingsFile().lastModified()
            if (modified != settingsModified) {
                settingsModified = modified
                engine.onEditorChord(if (modified == 0L) Engine.DEFAULT_EDITOR_CHORD else loadEditorChord(store))
            }
            pollFocus(engine, store)
        }

        private fun pollFocus(engine: Engine, store: Store) {
            val focused = Platform.focusedPackage()
            val valid = focused?.takeIf { Store.validPackage(it) }
            val fileModified = valid?.let { store.file(it).lastModified() } ?: -1L
            if (focused != pkg || fileModified != modified) {
                pkg = focused
                modified = fileModified
                layout = if (valid == null || fileModified == 0L) Layout.EMPTY else load(store, valid)
            }
            val (width, height) = Platform.displaySize()
            engine.onFocus(pkg, layout, width, height)
            val now = SystemClock.uptimeMillis()
            if (engine.aloneFor(APP_RESTART_AFTER_MS) && now - appStartedAt >= APP_RESTART_AFTER_MS) {
                Platform.startApp(engine.token, false)
                appStartedAt = now
            }
        }
    }

    private fun loadEditorChord(store: Store): String {
        try {
            val chord = JSONObject(store.readSettings()).optString("editorChord", Engine.DEFAULT_EDITOR_CHORD)
            if (LayoutJson.isChord(chord)) return chord
            Log.i("settings: unknown editor chord $chord; using ${Engine.DEFAULT_EDITOR_CHORD}")
        } catch (e: IOException) {
            Log.e("settings unreadable; editor chord ${Engine.DEFAULT_EDITOR_CHORD}", e)
        } catch (e: JSONException) {
            Log.e("settings unreadable; editor chord ${Engine.DEFAULT_EDITOR_CHORD}", e)
        }
        return Engine.DEFAULT_EDITOR_CHORD
    }

    private fun load(store: Store, pkg: String): Layout {
        try {
            val layout = LayoutJson.parse(JSONObject(store.read(pkg)))
            Log.i("layout for $pkg: ${layout.sets.size} mapping sets")
            return layout
        } catch (e: IOException) {
            Log.e("layout for $pkg unreadable; mapping off", e)
        } catch (e: JSONException) {
            Log.e("layout for $pkg unreadable; mapping off", e)
        }
        return Layout.EMPTY
    }
}
