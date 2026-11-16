package app.padmap.daemon;

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
import org.json.JSONException;
import org.json.JSONObject;

/** Loopback server for the app; a client that doesn't open with the daemon's token is dropped. */
final class Server {
    private final ServerSocket socket;
    private final Engine engine;

    /** Binds now, so a second daemon fails fast. */
    Server(Engine engine) throws IOException {
        this.engine = engine;
        socket = new ServerSocket(Protocol.PORT, 4, InetAddress.getLoopbackAddress());
    }

    void serve() throws IOException {
        while (true) {
            Socket connection = socket.accept();
            Thread thread = new Thread(new Client(connection), "padmap-client");
            thread.setDaemon(true);
            thread.start();
        }
    }

    final class Client implements Runnable {
        private final Socket connection;
        private Writer out;

        Client(Socket connection) {
            this.connection = connection;
        }

        void send(JSONObject message) {
            try {
                synchronized (this) {
                    out.write(message.toString());
                    out.write('\n');
                    out.flush();
                }
            } catch (IOException e) {
                close();
            }
        }

        @Override
        public void run() {
            boolean attached = false;
            try (Reader in = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                out = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
                if (!authenticated(readLine(in))) return;
                engine.attach(this);
                attached = true;
                for (String line = readLine(in); line != null; line = readLine(in)) {
                    engine.onMessage(this, new JSONObject(line));
                }
            } catch (IOException | JSONException e) {
                Log.i("client dropped: " + e.getMessage());
            } finally {
                if (attached) engine.detach(this);
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
            try {
                connection.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    private static String readLine(Reader in) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int c = in.read(); c != '\n'; c = in.read()) {
            if (c < 0) return line.length() == 0 ? null : line.toString();
            if (line.length() >= Protocol.MAX_LINE) throw new IOException("line too long");
            line.append((char) c);
        }
        return line.toString();
    }
}
