package app.padmap;

/**
 * Daemon <-> app messages: JSON objects, one per line, over TCP on loopback. The app's first line must be
 * {"t": "hello", "token": …} with the token the daemon passed when it started the app's service.
 * Daemon to app: state {package, pad, width, height, layout}, input {button | stick} (only while editing),
 * toggleEditor. App to daemon: hello, editing {on}, save {package, layout}.
 */
public final class Protocol {
    public static final String PACKAGE = "app.padmap";
    public static final String SERVICE = PACKAGE + "/.ui.OverlayService";
    public static final int PORT = 47130;
    public static final int MAX_LINE = 256 * 1024;
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_EDIT = "edit";

    private Protocol() {}
}
