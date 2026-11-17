# app_process starts the daemon by class name
-keep class app.padmap.daemon.Main {
    public static void main(java.lang.String[]);
}

# compile-only stubs (:hidden) of framework classes that only exist on the device
-dontwarn android.os.IVibratorManagerService**
-dontwarn android.os.IVibratorStateListener**
-dontwarn android.os.PermissionEnforcer
-dontwarn android.os.ServiceManager
-dontwarn android.os.VibratorInfo**
