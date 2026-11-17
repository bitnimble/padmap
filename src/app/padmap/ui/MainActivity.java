package app.padmap.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Setup and status: how to start the daemon, the overlay permission, and where layouts live. */
public final class MainActivity extends Activity {
    private static final long REFRESH_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refresh;
    private TextView status;
    private Button overlay;
    private String command;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        File script = installStartScript();
        command = script == null ? "adb shell sh <could not write start.sh>" : "adb shell sh " + script;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad, pad, pad);
        status = text(page, "");
        overlay = new Button(this);
        overlay.setText("Allow display over other apps");
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        page.addView(overlay);
        text(page, "Padmap turns gamepad input into touches, so you can play touch-only games with a controller.\n\n"
                + "1. Turn on USB debugging (Developer options) and connect to a computer with adb.\n"
                + "2. Run this on the computer, once after every phone restart:\n\n" + command + "\n\n"
                + "   Rooted: run the same file with su -c sh instead.\n"
                + "3. Open your game and press Select + Start on the gamepad to edit its layout. Layouts are plain "
                + "JSON in /sdcard/Padmap/<app package>.json.");
        Button copy = new Button(this);
        copy.setText("Copy command");
        copy.setOnClickListener(v -> getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipData.newPlainText("Padmap start command", command)));
        page.addView(copy);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
        startForegroundService(new Intent(this, OverlayService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void refresh() {
        status.setText(OverlayService.status);
        overlay.setEnabled(!Settings.canDrawOverlays(this));
        handler.postDelayed(refresh, REFRESH_MS);
    }

    private TextView text(LinearLayout parent, String content) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextIsSelectable(true);
        parent.addView(view);
        return view;
    }

    /** Copies start.sh where adb's shell user can read it. */
    private File installStartScript() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return null;
        File script = new File(dir, "start.sh");
        try (InputStream in = getAssets().open("start.sh"); OutputStream out = new FileOutputStream(script)) {
            byte[] buffer = new byte[8192];
            for (int n = in.read(buffer); n > 0; n = in.read(buffer)) out.write(buffer, 0, n);
            return script;
        } catch (IOException e) {
            return null;
        }
    }
}
