package app.padmap.daemon

internal object Log {
    private const val TAG = "Padmap"

    fun i(message: String) {
        android.util.Log.i(TAG, message)
        println(message)
    }

    fun e(message: String, error: Throwable) {
        android.util.Log.e(TAG, message, error)
        println("$message: $error")
    }
}
