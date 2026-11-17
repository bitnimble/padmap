package app.padmap.daemon

import android.os.SystemClock
import app.padmap.Protocol
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.json.JSONException
import org.json.JSONObject

/** Loopback server for the app; a client that doesn't present the daemon's token is dropped. */
internal class Server(private val engine: Engine) {
    // bound now, so a second daemon fails fast
    private val socket = ServerSocket(Protocol.PORT, 4, InetAddress.getLoopbackAddress())
    private val pending = AtomicInteger()
    private val attached = AtomicInteger()

    fun serve() {
        while (true) {
            val connection = try {
                socket.accept()
            } catch (e: IOException) {
                Log.e("accepting a client failed", e)
                SystemClock.sleep(ACCEPT_BACKOFF_MS)
                continue
            }
            if (pending.incrementAndGet() > MAX_PENDING) {
                pending.decrementAndGet()
                closeQuietly(connection)
                continue
            }
            val client = Client(connection)
            thread(isDaemon = true, name = "padmap-client") { client.run() }
        }
    }

    inner class Client(private val connection: Socket) {
        private val outgoing = ArrayBlockingQueue<String>(QUEUE)

        /** Queues a message; a client too slow to keep up is dropped rather than stalling the daemon. */
        fun send(message: JSONObject) {
            if (!outgoing.offer(message.toString())) close()
        }

        fun run() {
            var isPending = true
            var isAttached = false
            try {
                InputStreamReader(connection.getInputStream(), Charsets.UTF_8).use { input ->
                    val out = OutputStreamWriter(connection.getOutputStream(), Charsets.UTF_8)
                    thread(isDaemon = true, name = "padmap-client-writer") { write(out) }
                    send(JSONObject().put("t", "hello").put("proof", Protocol.proof(engine.token)))
                    connection.soTimeout = HELLO_TIMEOUT_MS.toInt()
                    val hello = readLine(input, HELLO_MAX, SystemClock.uptimeMillis() + HELLO_TIMEOUT_MS)
                    if (!authenticated(hello)) return
                    pending.decrementAndGet()
                    isPending = false
                    if (attached.incrementAndGet() > MAX_CLIENTS) {
                        attached.decrementAndGet()
                        return
                    }
                    connection.soTimeout = 0
                    engine.attach(this)
                    isAttached = true
                    while (true) {
                        val line = readLine(input, Protocol.MAX_LINE, Long.MAX_VALUE) ?: break
                        engine.onMessage(this, JSONObject(line))
                    }
                }
            } catch (e: IOException) {
                Log.i("client dropped: ${e.message}")
            } catch (e: JSONException) {
                Log.i("client dropped: ${e.message}")
            } catch (e: RuntimeException) {
                Log.e("client handling failed; dropping it", e)
            } finally {
                if (isAttached) {
                    engine.detach(this)
                    attached.decrementAndGet()
                }
                if (isPending) pending.decrementAndGet()
                close()
            }
        }

        private fun write(out: Writer) {
            try {
                while (!connection.isClosed) {
                    out.write(outgoing.take())
                    out.write('\n'.code)
                    if (outgoing.isEmpty()) out.flush()
                }
            } catch (e: IOException) {
                close()
            } catch (e: InterruptedException) {
                close()
            }
        }

        private fun authenticated(hello: String?): Boolean {
            if (hello == null) return false
            val message = JSONObject(hello)
            val given = message.optString("token").toByteArray()
            return message.optString("t") == "hello" && MessageDigest.isEqual(given, engine.token.toByteArray())
        }

        private fun close() {
            closeQuietly(connection)
            outgoing.clear()
            outgoing.offer("") // wakes the writer, whose write then fails on the closed socket and ends it
        }
    }

    private companion object {
        const val MAX_CLIENTS = 8
        const val MAX_PENDING = 32
        const val QUEUE = 256
        const val HELLO_MAX = 512
        const val HELLO_TIMEOUT_MS = 5000L
        const val ACCEPT_BACKOFF_MS = 1000L

        fun closeQuietly(connection: Socket) {
            try {
                connection.close()
            } catch (e: IOException) {
            }
        }

        fun readLine(input: Reader, max: Int, deadline: Long): String? {
            val line = StringBuilder()
            var c = input.read()
            while (c != '\n'.code) {
                if (c < 0) return if (line.isEmpty()) null else line.toString()
                if (line.length >= max) throw IOException("line too long")
                if (SystemClock.uptimeMillis() > deadline) throw IOException("too slow")
                line.append(c.toChar())
                c = input.read()
            }
            return line.toString()
        }
    }
}
