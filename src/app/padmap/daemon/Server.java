package app.padmap.daemon;

import android.os.SystemClock;
import app.padmap.Protocol;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONException;
import org.json.JSONObject;

/** Loopback server for the app; a client that doesn't present the daemon's token is dropped. */
final class Server {
    private static final int MAX_CLIENTS = 8, MAX_PENDING = 32, QUEUE = 256, HELLO_MAX = 512;
    private static final long HELLO_TIMEOUT_MS = 5000, ACCEPT_BACKOFF_MS = 1000;

    private final ServerSocket socket;
    private final Engine engine;
    private final AtomicInteger pending = new AtomicInteger(), attached = new AtomicInteger();

    /** Binds now, so a second daemon fails fast. */
    Server(Engine engine) throws IOException {
        this.engine = engine;
        socket = new ServerSocket(Protocol.PORT, 4, InetAddress.getLoopbackAddress());
    }

    void serve() {
        while (true) {
            Socket connection;
            try {
                connection = socket.accept();
            } catch (IOException e) {
                Log.e("accepting a client failed", e);
                SystemClock.sleep(ACCEPT_BACKOFF_MS);
                continue;
            }
            if (pending.incrementAndGet() > MAX_PENDING) {
                pending.decrementAndGet();
                closeQuietly(connection);
                continue;
            }
            start(new Client(connection)::run, "padmap-client");
        }
    }

    private static void closeQuietly(Socket connection) {
        try {
            connection.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private static void start(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    final class Client {
        private final Socket connection;
        private final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(QUEUE);

        Client(Socket connection) {
            this.connection = connection;
        }

        /** Queues a message; a client too slow to keep up is dropped rather than stalling the daemon. */
        void send(JSONObject message) {
            if (!outgoing.offer(message.toString())) close();
        }

        void run() {
            boolean isPending = true, isAttached = false;
            try (Reader in = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                Writer out = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
                start(() -> write(out), "padmap-client-writer");
                send(new JSONObject().put("t", "hello").put("proof", Protocol.proof(engine.token())));
                connection.setSoTimeout((int) HELLO_TIMEOUT_MS);
                String hello = readLine(in, HELLO_MAX, SystemClock.uptimeMillis() + HELLO_TIMEOUT_MS);
                if (!authenticated(hello)) return;
                pending.decrementAndGet();
                isPending = false;
                if (attached.incrementAndGet() > MAX_CLIENTS) {
                    attached.decrementAndGet();
                    return;
                }
                connection.setSoTimeout(0);
                engine.attach(this);
                isAttached = true;
                for (String line = readLine(in, Protocol.MAX_LINE, Long.MAX_VALUE); line != null;
                        line = readLine(in, Protocol.MAX_LINE, Long.MAX_VALUE)) {
                    engine.onMessage(this, new JSONObject(line));
                }
            } catch (IOException | JSONException e) {
                Log.i("client dropped: " + e.getMessage());
            } catch (RuntimeException e) {
                Log.e("client handling failed; dropping it", e);
            } finally {
                if (isAttached) {
                    engine.detach(this);
                    attached.decrementAndGet();
                }
                if (isPending) pending.decrementAndGet();
                close();
            }
        }

        private void write(Writer out) {
            try {
                while (!connection.isClosed()) {
                    out.write(outgoing.take());
                    out.write('\n');
                    if (outgoing.isEmpty()) out.flush();
                }
            } catch (IOException | InterruptedException e) {
                close();
            }
        }

        private boolean authenticated(String hello) throws JSONException {
            if (hello == null) return false;
            JSONObject message = new JSONObject(hello);
            byte[] given = message.optString("token").getBytes(StandardCharsets.UTF_8);
            return message.optString("t").equals("hello")
                    && MessageDigest.isEqual(given, engine.token().getBytes(StandardCharsets.UTF_8));
        }

        private void close() {
            closeQuietly(connection);
            outgoing.clear();
            outgoing.offer(""); // wakes the writer, whose write then fails on the closed socket and ends it
        }
    }

    private static String readLine(Reader in, int max, long deadline) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int c = in.read(); c != '\n'; c = in.read()) {
            if (c < 0) return line.length() == 0 ? null : line.toString();
            if (line.length() >= max) throw new IOException("line too long");
            if (SystemClock.uptimeMillis() > deadline) throw new IOException("too slow");
            line.append((char) c);
        }
        return line.toString();
    }
}
