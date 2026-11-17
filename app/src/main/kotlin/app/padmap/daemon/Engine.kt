package app.padmap.daemon

import android.os.SystemClock
import app.padmap.layout.Chord
import app.padmap.layout.Layout
import app.padmap.layout.LayoutJson
import app.padmap.layout.Mapper
import app.padmap.layout.PadState
import app.padmap.layout.SetSwitcher
import java.io.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class Engine(
    private val injector: TouchInjector,
    private val rumble: Rumble,
    private val store: Store,
    val token: String,
) {
    private val clients = HashSet<Server.Client>()
    private var gamepad: Gamepad? = null
    private var editor: Server.Client? = null
    private var focused: String? = null
    private var layout = Layout.EMPTY
    private var padName: String? = null
    private var pad = PadState.NEUTRAL
    private val switcher = SetSwitcher()
    private var editorChord = DEFAULT_EDITOR_CHORD
    private var width = 1
    private var height = 1
    private var aloneSince = SystemClock.uptimeMillis()

    @Synchronized
    fun setGamepad(gamepad: Gamepad) {
        this.gamepad = gamepad
        updateGrab()
    }

    @Synchronized
    fun onPad(next: PadState) {
        val previous = pad
        pad = next
        val editor = editor
        when {
            Chord.held(editorChord, next) && !Chord.held(editorChord, previous) -> toggleEditor()
            editor != null -> reportInput(editor, previous, next)
            else -> {
                if (switcher.update(layout, next)) {
                    injector.cancel()
                    Log.i("mapping set ${switcher.active + 1} of ${layout.sets.size}")
                    broadcastState()
                }
                retarget()
            }
        }
    }

    @Synchronized
    fun onPadConnected(name: String?) {
        padName = name
        rumble.setPad(name)
        broadcastState()
    }

    @Synchronized
    fun onFocus(pkg: String?, layout: Layout, width: Int, height: Int) {
        val changed = pkg != focused || layout != this.layout
        val resized = width != this.width || height != this.height
        if (!changed && !resized) return
        injector.cancel()
        if (pkg != focused || switcher.active >= layout.sets.size) switcher.reset()
        focused = pkg
        this.layout = layout
        this.width = width
        this.height = height
        updateGrab()
        broadcastState()
        retarget()
    }

    @Synchronized
    fun onEditorChord(chord: String) {
        if (chord == editorChord) return
        editorChord = chord
        Log.i("editor chord $chord")
        broadcastState()
    }

    @Synchronized
    fun attach(client: Server.Client) {
        clients += client
        client.send(state())
    }

    @Synchronized
    fun detach(client: Server.Client) {
        clients -= client
        if (clients.isEmpty()) aloneSince = SystemClock.uptimeMillis()
        if (editor === client) setEditor(null)
    }

    @Synchronized
    fun aloneFor(millis: Long): Boolean = clients.isEmpty() && SystemClock.uptimeMillis() - aloneSince >= millis

    fun onMessage(client: Server.Client, message: JSONObject) {
        when (val type = message.getString("t")) {
            "editing" -> setEditing(client, message.getBoolean("on"))
            "save" -> save(client, message.getString("package"), message.getJSONObject("layout"))
            "editorChord" -> saveEditorChord(message.getString("chord"))
            else -> Log.i("ignoring message $type")
        }
    }

    fun shutdown() {
        injector.cancelNow()
    }

    @Synchronized
    private fun setEditing(client: Server.Client, on: Boolean) {
        if (on) setEditor(client)
        else if (editor === client) setEditor(null)
    }

    private fun save(client: Server.Client, pkg: String, json: JSONObject) {
        val reply = message("saved").put("package", pkg)
        val failure = try {
            val saved = LayoutJson.parse(json)
            // file only, no layout = saved: the watcher alone sets the live layout; a second writer races its poll
            store.write(pkg, LayoutJson.toJson(saved).toString(2) + "\n")
            Log.i("saved ${saved.sets.size} mapping sets for $pkg")
            null
        } catch (e: IOException) {
            e
        } catch (e: JSONException) {
            e
        } catch (e: IllegalArgumentException) {
            e
        }
        if (failure != null) {
            Log.e("saving the layout for $pkg failed", failure)
            reply.put("error", failure.message.toString())
        }
        client.send(reply)
    }

    private fun saveEditorChord(chord: String) {
        if (!LayoutJson.isChord(chord)) {
            Log.i("ignoring editor chord $chord")
            return
        }
        try {
            store.writeSettings(JSONObject().put("editorChord", chord).toString(2) + "\n")
        } catch (e: IOException) {
            Log.e("saving the editor chord failed", e)
        }
        onEditorChord(chord)
    }

    private fun setEditor(client: Server.Client?) {
        editor = client
        injector.cancel()
        updateGrab()
        retarget()
    }

    private fun toggleEditor() {
        if (clients.isEmpty()) {
            Platform.startApp(token, true)
            return
        }
        for (client in clients.toList()) client.send(message("toggleEditor"))
    }

    private fun retarget() {
        if (editor != null) {
            injector.setTarget(emptyList(), width, height)
        } else {
            val controls = layout.sets[switcher.active].controls
            injector.setTarget(Mapper.contacts(controls, switcher.strip(pad), width / height.toDouble()), width, height)
        }
    }

    private fun updateGrab() {
        gamepad?.setGrab(editor != null || layout.maps())
        rumble.setMapping(editor == null && layout.maps())
    }

    private fun reportInput(editor: Server.Client, previous: PadState, next: PadState) {
        // the whole held set, so the editor can bind a chord from buttons pressed together (they arrive frame by frame)
        if (next.buttons != previous.buttons) editor.send(input("buttons", JSONArray(next.buttons)))
        for (stick in listOf("LS", "RS")) {
            if (next.stick(stick).magnitude() >= STICK_FLICK && previous.stick(stick).magnitude() < STICK_FLICK) {
                editor.send(input("stick", stick))
            }
        }
    }

    private fun broadcastState() {
        val state = state()
        for (client in clients.toList()) client.send(state)
    }

    private fun state(): JSONObject = message("state")
        .put("package", focused ?: JSONObject.NULL)
        .put("pad", padName ?: JSONObject.NULL)
        .put("width", width).put("height", height).put("set", switcher.active)
        .put("editorChord", editorChord)
        .put("layout", LayoutJson.toJson(layout))

    companion object {
        private const val STICK_FLICK = 0.6
        const val DEFAULT_EDITOR_CHORD = "SELECT+START"

        private fun input(kind: String, value: Any): JSONObject = message("input").put(kind, value)

        private fun message(type: String): JSONObject = JSONObject().put("t", type)
    }
}
