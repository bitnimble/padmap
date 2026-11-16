package app.padmap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Daemon <-> app messages: JSON objects, one per line, over TCP on loopback. The daemon opens with
 * {"t": "hello", "proof": proof(token)}, so the app only reveals its token to the real daemon; the app answers
 * {"t": "hello", "token": …} with the token the daemon passed when it started the app's service.
 * Daemon to app: state {package, pad, width, height, layout}, input {button | stick} (only while editing),
 * toggleEditor, saved {package, error?}. App to daemon: hello, editing {on}, save {package, layout}.
 */
public final class Protocol {
    public static final String PACKAGE = "app.padmap";
    public static final String SERVICE = PACKAGE + "/.ui.OverlayService";
    public static final int PORT = 47130;
    public static final int MAX_LINE = 256 * 1024;
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_EDIT = "edit";

    private Protocol() {}

    public static String proof(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((token + "|daemon").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
