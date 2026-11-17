package app.padmap.daemon

import android.content.ComponentName
import android.view.InputEvent
import app.padmap.Protocol
import java.io.FileDescriptor
import java.io.IOException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Hidden framework APIs, reached by reflection; app_process isn't subject to hidden-API enforcement. */
internal object Platform {
    private const val INJECT_MODE_WAIT_FOR_RESULT = 1
    private const val EXEC_TIMEOUT_S = 15L
    private const val EVIOCGRAB = 0x40044590
    private val TOP_ACTIVITY = Regex("(?:topResumedActivity|mResumedActivity)[=:] ?\\S+ \\S+ ([A-Za-z0-9._]+)/")

    private var inputManager: Any? = null
    private var injectInputEvent: Method? = null

    /** False if the dispatcher rejected the event. */
    fun inject(event: InputEvent): Boolean = try {
        val inject = injectInputEvent ?: bindInputManager()
        inject.invoke(inputManager, event, INJECT_MODE_WAIT_FOR_RESULT) as Boolean
    } catch (e: ReflectiveOperationException) {
        throw IllegalStateException("injectInputEvent failed", e)
    }

    private fun bindInputManager(): Method {
        val manager = try {
            Class.forName("android.hardware.input.InputManagerGlobal")
        } catch (e: ClassNotFoundException) {
            Class.forName("android.hardware.input.InputManager")
        }
        val instance = manager.getMethod("getInstance").invoke(null)
        inputManager = instance
        return instance.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            .also { injectInputEvent = it }
    }

    fun focusedPackage(): String? {
        try {
            val tasks = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
            // the interface, not tasks.javaClass: that's the private Stub.Proxy, and invoking a non-public
            // class's method can be refused, silently falling back to a dumpsys per second
            val service = Class.forName("android.app.IActivityTaskManager")
            val focused = try {
                service.getMethod("getFocusedRootTaskInfo")
            } catch (e: NoSuchMethodException) {
                service.getMethod("getFocusedStackInfo") // Android 11
            }
            val info = focused.invoke(tasks) ?: return null
            val top = info.javaClass.getField("topActivity").get(info) as ComponentName?
            return top?.packageName
        } catch (e: ReflectiveOperationException) {
            return focusedPackageFromDumpsys()
        }
    }

    private fun focusedPackageFromDumpsys(): String? = try {
        TOP_ACTIVITY.find(exec("dumpsys", "activity", "activities"))?.groupValues?.get(1)
    } catch (e: IOException) {
        null
    }

    /** Logical (rotated) size of the default display: [width, height]. */
    fun displaySize(): IntArray {
        try {
            val global = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val displays = global.getMethod("getInstance").invoke(null)
            val info = global.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(displays, 0)
            val type = info.javaClass
            return intArrayOf(type.getField("logicalWidth").getInt(info), type.getField("logicalHeight").getInt(info))
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("display info unavailable", e)
        }
    }

    /** Exclusive access to an evdev device until its fd is closed, so the focused app doesn't also get the pad. */
    fun grab(fd: FileDescriptor?) {
        try {
            val os = Class.forName("libcore.io.Libcore").getField("os").get(null)
            for (method in os.javaClass.methods) {
                if (method.name != "ioctlInt") continue
                val params = method.parameterTypes
                // either signature passes the kernel a pointer to an int; EVIOCGRAB only tests it for non-null
                if (params.size == 2) {
                    method.invoke(os, fd, EVIOCGRAB)
                    return
                }
                if (params.size == 3) {
                    method.invoke(os, fd, EVIOCGRAB, params[2].getConstructor(Int::class.javaPrimitiveType).newInstance(1))
                    return
                }
            }
            throw IOException("no ioctlInt in libcore")
        } catch (e: ReflectiveOperationException) {
            throw IOException("EVIOCGRAB failed", e.cause ?: e)
        }
    }

    /** Starts (or re-handshakes) the app's overlay service with this daemon's token; returns at once. */
    fun startApp(token: String, edit: Boolean) {
        thread(isDaemon = true, name = "padmap-start-app") {
            try {
                exec(
                    "am", "start-foreground-service", "-n", Protocol.SERVICE, "--es", Protocol.EXTRA_TOKEN, token,
                    "--ez", Protocol.EXTRA_EDIT, edit.toString(),
                )
            } catch (e: IOException) {
                Log.e("starting the app failed", e)
            }
        }
    }

    fun grantPermissions() {
        grant("the overlay permission", "appops", "set", Protocol.PACKAGE, "SYSTEM_ALERT_WINDOW", "allow")
        // without it Android drops the app's toasts while the game has the foreground
        grant("the notification permission", "pm", "grant", Protocol.PACKAGE, "android.permission.POST_NOTIFICATIONS")
    }

    private fun grant(what: String, vararg command: String) {
        try {
            exec(*command)
        } catch (e: IOException) {
            Log.e("granting $what failed", e)
        }
    }

    fun exec(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val watchdog = thread(isDaemon = true, name = "padmap-exec-watchdog") {
            try {
                if (!process.waitFor(EXEC_TIMEOUT_S, TimeUnit.SECONDS)) process.destroyForcibly()
            } catch (e: InterruptedException) {
                // exec finished first
            }
        }
        try {
            return process.inputStream.use { input ->
                val output = input.reader().readText()
                process.waitFor()
                output
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        } finally {
            watchdog.interrupt()
        }
    }
}
