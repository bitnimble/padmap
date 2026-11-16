package app.padmap.ui;

import android.os.Handler;
import android.os.Looper;
import app.padmap.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

/** Keeps a connection to the daemon, reconnecting until stopped; callbacks run on the main thread. */
final class DaemonClient {
    private static final long RETRY_MS = 2000;

    /** Callbacks name their client, so ones still queued from a replaced client can be ignored. */
    interface Listener {
        void onMessage(DaemonClient source, JSONObject message);

        void onDisconnected(DaemonClient source);
    }

    private final String token;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private volatile Socket socket;
    private volatile Writer out;

    DaemonClient(String token, Listener listener) {
        this.token = token;
        this.listener = listener;
        Thread thread = new Thread(this::loop, "padmap-daemon");
        thread.setDaemon(true);
        thread.start();
    }

    void send(JSONObject message) {
        sender.execute(() -> {
            Writer writer = out;
            if (writer == null) return;
            try {
                writer.write(message.toString());
                writer.write('\n');
                writer.flush();
            } catch (IOException e) {
                closeSocket();
            }
        });
    }

    void stop() {
        running = false;
        closeSocket();
        sender.shutdown();
    }

    private void loop() {
        while (running) {
            try (Socket connection = new Socket(InetAddress.getLoopbackAddress(), Protocol.PORT)) {
                socket = connection;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                String hello = in.readLine();
                // only the daemon that issued the token can prove it; anything else on the port doesn't get it
                if (hello == null || !Protocol.proof(token).equals(new JSONObject(hello).optString("proof"))) {
                    throw new IOException("not our daemon");
                }
                Writer writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
                writer.write(new JSONObject().put("t", "hello").put("token", token) + "\n");
                writer.flush();
                out = writer;
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    JSONObject message = new JSONObject(line);
                    main.post(() -> listener.onMessage(this, message));
                }
            } catch (IOException | JSONException e) {
                // daemon not running yet, restarted with a new token, or an impostor: retry
            }
            out = null;
            socket = null;
            main.post(() -> listener.onDisconnected(this));
            if (running) android.os.SystemClock.sleep(RETRY_MS);
        }
    }

    private void closeSocket() {
        Socket current = socket;
        if (current == null) return;
        try {
            current.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
