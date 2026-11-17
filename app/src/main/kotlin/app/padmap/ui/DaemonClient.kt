package app.padmap.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.padmap.Protocol
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors
import org.json.JSONException
import org.json.JSONObject

/** Keeps a connection to the daemon, reconnecting until stopped; callbacks run on the main thread. */
class DaemonClient(private val token: String, private val listener: Listener) {
    /** Callbacks name their client, so ones still queued from a replaced client can be ignored. */
    interface Listener {
        fun onMessage(source: DaemonClient, message: JSONObject)

        fun onDisconnected(source: DaemonClient)
    }

    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor()

    @Volatile private var running = true

    @Volatile private var socket: Socket? = null

    @Volatile private var out: Writer? = null

    init {
        Thread(::loop, "padmap-daemon").apply { isDaemon = true }.start()
    }

    fun send(message: JSONObject) {
        if (!running) return
        sender.execute {
            val writer = out ?: return@execute
            try {
                writer.write(message.toString())
                writer.write('\n'.code)
                writer.flush()
            } catch (_: IOException) {
                closeSocket()
            }
        }
    }

    fun stop() {
        running = false
        closeSocket()
        sender.shutdown()
    }

    private fun loop() {
        while (running) {
            try {
                Socket(InetAddress.getLoopbackAddress(), Protocol.PORT).use { connection ->
                    socket = connection
                    if (!running) return@use
                    val input = connection.getInputStream().reader(Charsets.UTF_8)
                    connection.soTimeout = HELLO_TIMEOUT_MS
                    val hello = readLine(input, HELLO_MAX)
                    // only the daemon that issued the token can prove it; anything else on the port doesn't get it
                    if (hello == null || Protocol.proof(token) != JSONObject(hello).optString("proof")) {
                        throw IOException("not our daemon")
                    }
                    connection.soTimeout = 0
                    val writer = connection.getOutputStream().writer(Charsets.UTF_8)
                    writer.write("${JSONObject().put("t", "hello").put("token", token)}\n")
                    writer.flush()
                    out = writer
                    while (true) {
                        val message = JSONObject(readLine(input, Protocol.MAX_LINE) ?: break)
                        main.post { listener.onMessage(this, message) }
                    }
                }
            } catch (_: IOException) {
            } catch (_: JSONException) {
            }
            out = null
            socket = null
            main.post { listener.onDisconnected(this) }
            if (running) SystemClock.sleep(RETRY_MS)
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val RETRY_MS = 2000L
        const val HELLO_TIMEOUT_MS = 5000
        const val HELLO_MAX = 512

        fun readLine(input: Reader, max: Int): String? {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == '\n'.code) return line.toString()
                if (c < 0) return if (line.isEmpty()) null else line.toString()
                if (line.length >= max) throw IOException("line too long")
                line.append(c.toChar())
            }
        }
    }
}
