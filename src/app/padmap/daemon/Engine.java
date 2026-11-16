package app.padmap.daemon;

import android.os.SystemClock;
import app.padmap.layout.Layout;
import app.padmap.layout.LayoutJson;
import app.padmap.layout.Mapper;
import app.padmap.layout.PadState;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** The daemon's state: focused app, its layout, the pad, connected app clients and whether one is editing. */
final class Engine {
    private static final double STICK_FLICK = 0.6;

    private final TouchInjector injector;
    private final Store store;
    private final String token;
    private final Set<Server.Client> clients = new HashSet<>();
    private Gamepad gamepad;
    private Server.Client editor;
    private String focused;
    private Layout layout = Layout.EMPTY;
    private String padName;
    private PadState pad = PadState.NEUTRAL;
    private int width = 1, height = 1;
    private long aloneSince = SystemClock.uptimeMillis();

    Engine(TouchInjector injector, Store store, String token) {
        this.injector = injector;
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
        if (chord(next) && !chord(previous)) {
            toggleEditor();
        } else if (editor != null) {
            reportInput(previous, next);
        } else {
            retarget();
        }
    }

    synchronized void onPadConnected(String name) {
        padName = name;
        broadcastState();
    }

    synchronized void onFocus(String pkg, Layout layout, int width, int height) {
        boolean changed = !Objects.equals(pkg, focused) || !layout.equals(this.layout);
        boolean resized = width != this.width || height != this.height;
        if (!changed && !resized) return;
        if (changed) injector.cancel();
        focused = pkg;
        this.layout = layout;
        this.width = width;
        this.height = height;
        updateGrab();
        broadcastState();
        retarget();
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

    synchronized void onMessage(Server.Client client, JSONObject message) throws JSONException {
        switch (message.getString("t")) {
            case "editing" -> {
                if (message.getBoolean("on")) setEditor(client);
                else if (editor == client) setEditor(null);
            }
            case "save" -> save(message.getString("package"), LayoutJson.parse(message.getJSONObject("layout")));
            default -> Log.i("ignoring message " + message.getString("t"));
        }
    }

    void shutdown() {
        injector.cancel();
    }

    String token() {
        return token;
    }

    private void save(String pkg, Layout saved) {
        try {
            store.write(pkg, LayoutJson.toJson(saved).toString(2) + "\n");
        } catch (IOException | JSONException | IllegalArgumentException e) {
            Log.e("saving the layout for " + pkg + " failed", e);
            return;
        }
        Log.i("saved " + saved.controls().size() + " controls for " + pkg);
        if (pkg.equals(focused)) layout = saved;
        updateGrab();
        broadcastState();
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
            injector.setTarget(Mapper.contacts(layout, pad, width / (double) height), width, height);
        }
    }

    private void updateGrab() {
        if (gamepad != null) gamepad.setGrab(editor != null || !layout.controls().isEmpty());
    }

    private void reportInput(PadState previous, PadState next) {
        for (String button : next.buttons()) {
            if (!previous.pressed(button)) editor.send(input("button", button));
        }
        for (String stick : new String[] {"LS", "RS"}) {
            if (next.stick(stick).magnitude() >= STICK_FLICK && previous.stick(stick).magnitude() < STICK_FLICK) {
                editor.send(input("stick", stick));
            }
        }
    }

    private static JSONObject input(String kind, String name) {
        try {
            return message("input").put(kind, name);
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
                    .put("width", width).put("height", height)
                    .put("layout", LayoutJson.toJson(layout));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean chord(PadState state) {
        return state.pressed("SELECT") && state.pressed("START");
    }

    private static JSONObject message(String type) {
        try {
            return new JSONObject().put("t", type);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
