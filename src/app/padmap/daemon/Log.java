package app.padmap.daemon;

final class Log {
    private static final String TAG = "Padmap";

    private Log() {}

    static void i(String message) {
        android.util.Log.i(TAG, message);
        System.out.println(message);
    }

    static void e(String message, Throwable error) {
        android.util.Log.e(TAG, message, error);
        System.out.println(message + ": " + error);
    }
}
