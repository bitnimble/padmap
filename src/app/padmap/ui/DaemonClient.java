package app.padmap.ui;

import android.os.Handler;
import android.os.Looper;
import app.padmap.Protocol;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
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
    private static final int HELLO_TIMEOUT_MS = 5000, HELLO_MAX = 512;

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
                if (!running) break;
                Reader in = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
                connection.setSoTimeout(HELLO_TIMEOUT_MS);
                String hello = readLine(in, HELLO_MAX);
                // only the daemon that issued the token can prove it; anything else on the port doesn't get it
                if (hello == null || !Protocol.proof(token).equals(new JSONObject(hello).optString("proof"))) {
                    throw new IOException("not our daemon");
                }
                connection.setSoTimeout(0);
                Writer writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
                writer.write(new JSONObject().put("t", "hello").put("token", token) + "\n");
                writer.flush();
                out = writer;
                for (String line = readLine(in, Protocol.MAX_LINE); line != null;
                        line = readLine(in, Protocol.MAX_LINE)) {
                    JSONObject message = new JSONObject(line);
                    main.post(() -> listener.onMessage(this, message));
                }
            } catch (IOException | JSONException ignored) {
            }
            out = null;
            socket = null;
            main.post(() -> listener.onDisconnected(this));
            if (running) android.os.SystemClock.sleep(RETRY_MS);
        }
    }

    private static String readLine(Reader in, int max) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int c = in.read(); c != '\n'; c = in.read()) {
            if (c < 0) return line.length() == 0 ? null : line.toString();
            if (line.length() >= max) throw new IOException("line too long");
            line.append((char) c);
        }
        return line.toString();
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
