package app.padmap.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.padmap.Protocol
import app.padmap.layout.Layout
import app.padmap.layout.LayoutJson
import org.json.JSONException
import org.json.JSONObject

/** Holds the daemon connection and the editor overlay, which opens on the editor chord. */
class OverlayService : LifecycleService(), DaemonClient.Listener, SavedStateRegistryOwner {
    private class OpenEditor(
        val view: ComposeView,
        val store: EditorStore,
        val presenter: EditorPresenter,
        val pkg: String,
    )

    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private lateinit var windows: WindowManager
    private lateinit var prefs: SharedPreferences
    private var client: DaemonClient? = null
    private var token: String? = null
    private var state: JSONObject? = null
    private var editor: OpenEditor? = null
    private var editWhenConnected = false

    override fun onCreate() {
        savedState.performAttach()
        savedState.performRestore(null)
        super.onCreate()
        windows = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences("padmap", MODE_PRIVATE)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Padmap overlay", NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Padmap is mapping your gamepad")
            .build()
        startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val given = intent?.getStringExtra(Protocol.EXTRA_TOKEN)
        if (given != null) prefs.edit().putString(PREF_TOKEN, given).apply()
        val current = given ?: prefs.getString(PREF_TOKEN, null)
        if (current != null && current != token) {
            token = current
            client?.stop()
            client = DaemonClient(current, this)
        }
        if (intent?.getBooleanExtra(Protocol.EXTRA_EDIT, false) == true) {
            if (state != null) openEditor() else editWhenConnected = true
        }
        // not sticky: a background restart can't start a foreground service; the daemon restarts us instead
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        closeEditor()
        client?.stop()
        super.onDestroy()
    }

    override fun onMessage(source: DaemonClient, message: JSONObject) {
        if (source !== client) return
        when (message.optString("t")) {
            "state" -> onState(message)
            "input" -> {
                val presenter = editor?.presenter ?: return
                message.optJSONArray("buttons")?.let { held -> presenter.onButtons(List(held.length(), held::optString)) }
                if (message.has("stick")) presenter.onStick(message.optString("stick"))
            }
            "toggleEditor" -> if (editor == null) openEditor() else close()
            "saved" -> toast(
                if (message.has("error")) "Couldn't save the layout: ${message.optString("error")}"
                else "Saved the layout for ${message.optString("package")}",
            )
        }
    }

    override fun onDisconnected(source: DaemonClient) {
        if (source !== client) return
        state = null
        status = "Not connected to the Padmap daemon. Start it with the command on Padmap's main screen."
        if (editor?.presenter?.changedLayout() != null) {
            toast("Padmap's daemon stopped; your layout changes weren't saved", Toast.LENGTH_LONG)
        }
        closeEditor()
    }

    private fun close() {
        val open = editor ?: return
        open.presenter.changedLayout()?.let { save(open.pkg, it) }
        closeEditor()
    }

    private fun save(pkg: String, layout: Layout) {
        client?.send(JSONObject().put("t", "save").put("package", pkg).put("layout", LayoutJson.toJson(layout)))
    }

    private fun saveEditorChord(chord: String) {
        client?.send(JSONObject().put("t", "editorChord").put("chord", chord))
    }

    private fun onState(message: JSONObject) {
        val previous = state
        state = message
        val pkg = packageOf(message)
        val set = message.optInt("set")
        if (editor == null && previous != null && pkg != null && pkg == packageOf(previous) &&
            set != previous.optInt("set")
        ) {
            toast("Padmap: mapping set ${set + 1}")
        }
        val pad = if (message.isNull("pad")) "no gamepad found" else "gamepad ${message.optString("pad")}"
        status = "Connected: $pad, focused app ${pkg ?: "none"}."
        editor?.presenter?.apply {
            setDisplaySize(message.optInt("width"), message.optInt("height"))
            setEditorChord(message.optString("editorChord"))
        }
        if (editWhenConnected) {
            editWhenConnected = false
            openEditor()
        }
    }

    private fun openEditor() {
        val current = state ?: return
        val pkg = packageOf(current)
        if (editor != null || pkg == null || pkg == packageName || !Settings.canDrawOverlays(this)) return
        val layout = try {
            LayoutJson.parse(current.getJSONObject("layout"))
        } catch (_: JSONException) {
            Layout.EMPTY
        }
        val style = prefs.getString(PREF_STYLE, null)?.let { saved -> ButtonStyle.entries.firstOrNull { it.name == saved } }
        val store = EditorStore(
            layout,
            current.optInt("set"),
            current.optString("editorChord"),
            style ?: ButtonStyle.XBOX,
            current.optInt("width"),
            current.optInt("height"),
        )
        val presenter = EditorPresenter(
            store,
            saveStyle = { prefs.edit().putString(PREF_STYLE, it.name).apply() },
            saveEditorChord = ::saveEditorChord,
            notify = ::toast,
        )
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent { PadmapTheme { Editor(store, presenter, ::close) } }
        }
        windows.addView(view, overlayParams())
        editor = OpenEditor(view, store, presenter, pkg)
        sendEditing(true)
    }

    private fun closeEditor() {
        val open = editor ?: return
        windows.removeView(open.view)
        editor = null
        sendEditing(false)
    }

    private fun sendEditing(on: Boolean) {
        client?.send(JSONObject().put("t", "editing").put("on", on))
    }

    private fun toast(text: String) = toast(text, Toast.LENGTH_SHORT)

    private fun toast(text: String, duration: Int) {
        Toast.makeText(this, text, duration).show()
    }

    companion object {
        private const val CHANNEL = "padmap"
        private const val NOTIFICATION = 1
        private const val PREF_TOKEN = "token"
        private const val PREF_STYLE = "buttonStyle"

        @Volatile
        var status = "Not connected to the Padmap daemon."
            private set

        private fun packageOf(state: JSONObject): String? =
            if (state.isNull("package")) null else state.optString("package")

        private fun overlayParams(): WindowManager.LayoutParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                // the editor maps raw screen coordinates to the display; insets or a cutout margin would offset it
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            }
    }
}
