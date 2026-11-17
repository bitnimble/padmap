package app.padmap

import java.security.MessageDigest

/**
 * Daemon <-> app messages: JSON objects, one per line, over TCP on loopback. The daemon opens with
 * {"t": "hello", "proof": proof(token)}, so the app only reveals its token to the real daemon; the app answers
 * {"t": "hello", "token": …} with the token the daemon passed when it started the app's service.
 * Daemon to app: state {package, pad, width, height, set (active mapping set's index), editorChord, layout}, input
 * {button | stick} (only while editing), toggleEditor, saved {package, error?}. App to daemon: hello, editing {on},
 * save {package, layout}, editorChord {chord}.
 */
object Protocol {
    const val PACKAGE = "app.padmap"
    const val SERVICE = "$PACKAGE/.ui.OverlayService"
    const val PORT = 47130
    const val MAX_LINE = 256 * 1024
    const val EXTRA_TOKEN = "token"
    const val EXTRA_EDIT = "edit"

    fun proof(token: String): String =
        MessageDigest.getInstance("SHA-256").digest("$token|daemon".toByteArray()).joinToString("") { "%02x".format(it) }
}
