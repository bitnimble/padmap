package app.padmap.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import app.padmap.Protocol;
import app.padmap.layout.Layout;
import app.padmap.layout.LayoutJson;
import org.json.JSONException;
import org.json.JSONObject;

/** Holds the daemon connection and the overlays: a draggable bubble that opens the editor, and the editor itself. */
public final class OverlayService extends Service implements DaemonClient.Listener, EditorView.Callbacks {
    private static final String CHANNEL = "padmap";
    private static final int NOTIFICATION = 1;
    private static final String PREF_TOKEN = "token";

    static volatile String status = "Not connected to the Padmap daemon.";

    private WindowManager windows;
    private SharedPreferences prefs;
    private DaemonClient client;
    private String token;
    private JSONObject state;
    private View bubble;
    private EditorView editor;
    private String editing;
    private boolean editWhenConnected;

    @Override
    public void onCreate() {
        super.onCreate();
        windows = getSystemService(WindowManager.class);
        prefs = getSharedPreferences("padmap", MODE_PRIVATE);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(
                new NotificationChannel(CHANNEL, "Padmap overlay", NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Padmap is mapping your gamepad")
                .build();
        startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String given = intent == null ? null : intent.getStringExtra(Protocol.EXTRA_TOKEN);
        if (given != null) prefs.edit().putString(PREF_TOKEN, given).apply();
        String current = given != null ? given : prefs.getString(PREF_TOKEN, null);
        if (current != null && !current.equals(token)) {
            token = current;
            if (client != null) client.stop();
            client = new DaemonClient(token, this);
        }
        if (intent != null && intent.getBooleanExtra(Protocol.EXTRA_EDIT, false)) {
            if (state != null) openEditor();
            else editWhenConnected = true;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (client != null) client.stop();
        closeEditor();
        showBubble(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onMessage(JSONObject message) {
        switch (message.optString("t")) {
            case "state" -> {
                state = message;
                String pkg = focusedPackage();
                String pad = message.isNull("pad") ? "no gamepad found" : "gamepad " + message.optString("pad");
                status = "Connected: " + pad + ", focused app " + (pkg == null ? "none" : pkg) + ".";
                if (editWhenConnected) {
                    editWhenConnected = false;
                    openEditor();
                }
                showBubble(editor == null && pkg != null && !pkg.equals(getPackageName()));
            }
            case "input" -> {
                if (editor == null) return;
                if (message.has("button")) editor.onButton(message.optString("button"));
                if (message.has("stick")) editor.onStick(message.optString("stick"));
            }
            case "toggleEditor" -> {
                if (editor == null) openEditor();
                else close();
            }
            default -> {}
        }
    }

    @Override
    public void onDisconnected() {
        state = null;
        status = "Not connected to the Padmap daemon. Start it with the command on Padmap's main screen.";
        closeEditor();
        showBubble(false);
    }

    @Override
    public void save(Layout layout) {
        if (client == null || editing == null) return;
        try {
            client.send(new JSONObject().put("t", "save").put("package", editing).put("layout", LayoutJson.toJson(layout)));
            Toast.makeText(this, "Saved the layout for " + editing, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        closeEditor();
        showBubble(focusedPackage() != null);
    }

    private void openEditor() {
        String pkg = focusedPackage();
        if (editor != null || pkg == null || pkg.equals(getPackageName()) || !Settings.canDrawOverlays(this)) return;
        Layout layout;
        try {
            layout = LayoutJson.parse(state.getJSONObject("layout"));
        } catch (JSONException e) {
            layout = Layout.EMPTY;
        }
        editing = pkg;
        editor = new EditorView(this, pkg, layout, state.optInt("width"), state.optInt("height"), this);
        WindowManager.LayoutParams params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        windows.addView(editor, params);
        showBubble(false);
        sendEditing(true);
    }

    private void closeEditor() {
        if (editor == null) return;
        windows.removeView(editor);
        editor = null;
        editing = null;
        sendEditing(false);
    }

    private void sendEditing(boolean on) {
        if (client == null) return;
        try {
            client.send(new JSONObject().put("t", "editing").put("on", on));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private String focusedPackage() {
        return state == null || state.isNull("package") ? null : state.optString("package");
    }

    private void showBubble(boolean show) {
        if (show == (bubble != null)) return;
        if (!show) {
            windows.removeView(bubble);
            bubble = null;
            return;
        }
        if (!Settings.canDrawOverlays(this)) return;
        int size = (int) (48 * getResources().getDisplayMetrics().density);
        TextView view = new TextView(this);
        view.setText("✥");
        view.setTextColor(Color.WHITE);
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.argb(150, 30, 30, 30));
        view.setBackground(circle);
        WindowManager.LayoutParams params = overlayParams(size, size);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = size / 2;
        params.y = size * 2;
        view.setOnTouchListener(new BubbleDrag(params));
        windows.addView(view, params);
        bubble = view;
    }

    private static WindowManager.LayoutParams overlayParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        // the editor maps raw screen coordinates to the display; insets or a cutout margin would offset it
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setFitInsetsTypes(0);
        return params;
    }

    /** Drags the bubble; a press that doesn't move opens the editor. */
    private final class BubbleDrag implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private float startX, startY;
        private int originX, originY;
        private boolean moved;

        BubbleDrag(WindowManager.LayoutParams params) {
            this.params = params;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    originX = params.x;
                    originY = params.y;
                    moved = false;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                    moved |= Math.hypot(dx, dy) > view.getWidth() / 4f;
                    if (!moved) break;
                    params.x = originX + (int) dx;
                    params.y = originY + (int) dy;
                    windows.updateViewLayout(view, params);
                }
                case MotionEvent.ACTION_UP -> {
                    if (!moved) openEditor();
                }
                default -> {}
            }
            return true;
        }
    }
}
