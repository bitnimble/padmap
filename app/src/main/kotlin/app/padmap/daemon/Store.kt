package app.padmap.daemon

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class Store {
    private val dir = File(ROOTS.firstOrNull { File(it).isDirectory } ?: ROOTS.last(), "Padmap")

    fun file(pkg: String): File {
        require(validPackage(pkg)) { "bad package name: $pkg" }
        return File(dir, "$pkg.json")
    }

    fun settingsFile(): File = File(dir, SETTINGS)

    fun read(pkg: String): String = file(pkg).readText()

    fun readSettings(): String = settingsFile().readText()

    fun write(pkg: String, json: String) = write(file(pkg), json)

    fun writeSettings(json: String) = write(settingsFile(), json)

    @Synchronized
    private fun write(target: File, json: String) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("can't create $dir")
        val temp = File(dir, ".${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(json.toByteArray())
            out.fd.sync()
        }
        if (!temp.renameTo(target)) throw IOException("can't replace $target")
    }

    companion object {
        private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        // root outside an app's mount namespace (Lepton, su) has no /sdcard; the backing dir is the same storage
        private val ROOTS = listOf("/sdcard", "/storage/emulated/0", "/data/media/0")
        private const val SETTINGS = "settings.json"

        fun validPackage(name: String): Boolean = name.length <= 255 && PACKAGE.matches(name)
    }
}
