package app.padmap.daemon;

import android.os.SystemClock;
import app.padmap.layout.Chord;
import app.padmap.layout.Control;
import app.padmap.layout.Layout;
import app.padmap.layout.LayoutJson;
import app.padmap.layout.Mapper;
import app.padmap.layout.PadState;
import app.padmap.layout.SetSwitcher;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The daemon's state: focused app, its layout, the pad, connected app clients and whether one is editing. */
final class Engine {
    private static final double STICK_FLICK = 0.6;
    static final String DEFAULT_EDITOR_CHORD = "SELECT+START";

    private final TouchInjector injector;
    private final Rumble rumble;
    private final Store store;
    private final String token;
    private final Set<Server.Client> clients = new HashSet<>();
    private Gamepad gamepad;
    private Server.Client editor;
    private String focused;
    private Layout layout = Layout.EMPTY;
    private String padName;
    private PadState pad = PadState.NEUTRAL;
    private final SetSwitcher switcher = new SetSwitcher();
    private String editorChord = DEFAULT_EDITOR_CHORD;
    private int width = 1, height = 1;
    private long aloneSince = SystemClock.uptimeMillis();

    Engine(TouchInjector injector, Rumble rumble, Store store, String token) {
        this.injector = injector;
        this.rumble = rumble;
        this.store = store;
        this.token = token;
    }

    synchronized void setGamepad(Gamepad gamepad) {
        this.gamepad = gamepad;
        updateGrab();
    }

    synchronized void onPad(PadState next) {
        PadState previous = pad;
        pad = next;
        if (Chord.held(editorChord, next) && !Chord.held(editorChord, previous)) {
            toggleEditor();
        } else if (editor != null) {
            reportInput(previous, next);
        } else {
            if (switcher.update(layout, next)) {
                injector.cancel();
                Log.i("mapping set " + (switcher.active() + 1) + " of " + layout.sets().size());
                broadcastState();
            }
            retarget();
        }
    }

    synchronized void onPadConnected(String name) {
        padName = name;
        rumble.setPad(name);
        broadcastState();
    }

    synchronized void onFocus(String pkg, Layout layout, int width, int height) {
        boolean changed = !Objects.equals(pkg, focused) || !layout.equals(this.layout);
        boolean resized = width != this.width || height != this.height;
        if (!changed && !resized) return;
        injector.cancel();
        if (!Objects.equals(pkg, focused) || switcher.active() >= layout.sets().size()) switcher.reset();
        focused = pkg;
        this.layout = layout;
        this.width = width;
        this.height = height;
        updateGrab();
        broadcastState();
        retarget();
    }

    synchronized void onEditorChord(String chord) {
        if (chord.equals(editorChord)) return;
        editorChord = chord;
        Log.i("editor chord " + chord);
        broadcastState();
    }

    synchronized void attach(Server.Client client) {
        clients.add(client);
        client.send(state());
    }

    synchronized void detach(Server.Client client) {
        clients.remove(client);
        if (clients.isEmpty()) aloneSince = SystemClock.uptimeMillis();
        if (editor == client) setEditor(null);
    }

    synchronized boolean aloneFor(long millis) {
        return clients.isEmpty() && SystemClock.uptimeMillis() - aloneSince >= millis;
    }

    void onMessage(Server.Client client, JSONObject message) throws JSONException {
        switch (message.getString("t")) {
            case "editing" -> setEditing(client, message.getBoolean("on"));
            case "save" -> save(client, message.getString("package"), message.getJSONObject("layout"));
            case "editorChord" -> saveEditorChord(message.getString("chord"));
            default -> Log.i("ignoring message " + message.getString("t"));
        }
    }

    void shutdown() {
        injector.cancelNow();
    }

    String token() {
        return token;
    }

    private synchronized void setEditing(Server.Client client, boolean on) {
        if (on) setEditor(client);
        else if (editor == client) setEditor(null);
    }

    private void save(Server.Client client, String pkg, JSONObject json) throws JSONException {
        JSONObject reply = message("saved").put("package", pkg);
        try {
            Layout saved = LayoutJson.parse(json);
            // file only, no layout = saved: the watcher alone sets the live layout; a second writer races its poll
            store.write(pkg, LayoutJson.toJson(saved).toString(2) + "\n");
            Log.i("saved " + saved.sets().size() + " mapping sets for " + pkg);
        } catch (IOException | JSONException | IllegalArgumentException e) {
            Log.e("saving the layout for " + pkg + " failed", e);
            reply.put("error", String.valueOf(e.getMessage()));
        }
        client.send(reply);
    }

    private void saveEditorChord(String chord) throws JSONException {
        if (!LayoutJson.isChord(chord)) {
            Log.i("ignoring editor chord " + chord);
            return;
        }
        try {
            store.writeSettings(new JSONObject().put("editorChord", chord).toString(2) + "\n");
        } catch (IOException e) {
            Log.e("saving the editor chord failed", e);
        }
        onEditorChord(chord);
    }

    private void setEditor(Server.Client client) {
        editor = client;
        injector.cancel();
        updateGrab();
        retarget();
    }

    private void toggleEditor() {
        if (clients.isEmpty()) {
            Platform.startApp(token, true);
            return;
        }
        for (Server.Client client : List.copyOf(clients)) client.send(message("toggleEditor"));
    }

    private void retarget() {
        if (editor != null) {
            injector.setTarget(List.of(), width, height);
        } else {
            List<Control> controls = layout.sets().get(switcher.active()).controls();
            injector.setTarget(Mapper.contacts(controls, switcher.strip(pad), width / (double) height), width, height);
        }
    }

    private void updateGrab() {
        if (gamepad != null) gamepad.setGrab(editor != null || layout.maps());
        rumble.setMapping(editor == null && layout.maps());
    }

    private void reportInput(PadState previous, PadState next) {
        // the whole held set, so the editor can bind a chord from buttons pressed together (they arrive frame by frame)
        if (!next.buttons().equals(previous.buttons())) editor.send(input("buttons", new JSONArray(next.buttons())));
        for (String stick : new String[] {"LS", "RS"}) {
            if (next.stick(stick).magnitude() >= STICK_FLICK && previous.stick(stick).magnitude() < STICK_FLICK) {
                editor.send(input("stick", stick));
            }
        }
    }

    private static JSONObject input(String kind, Object value) {
        try {
            return message("input").put(kind, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private void broadcastState() {
        JSONObject state = state();
        for (Server.Client client : List.copyOf(clients)) client.send(state);
    }

    private JSONObject state() {
        try {
            return message("state").put("package", focused == null ? JSONObject.NULL : focused)
                    .put("pad", padName == null ? JSONObject.NULL : padName)
                    .put("width", width).put("height", height).put("set", switcher.active())
                    .put("editorChord", editorChord)
                    .put("layout", LayoutJson.toJson(layout));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject message(String type) {
        try {
            return new JSONObject().put("t", type);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
