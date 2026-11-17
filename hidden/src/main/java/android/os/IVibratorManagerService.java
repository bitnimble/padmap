package android.os;

public interface IVibratorManagerService extends IInterface {
    int[] getVibratorIds() throws RemoteException;

    VibratorInfo getVibratorInfo(int vibratorId) throws RemoteException;

    boolean isVibrating(int vibratorId) throws RemoteException;

    boolean registerVibratorStateListener(int vibratorId, IVibratorStateListener listener) throws RemoteException;

    boolean unregisterVibratorStateListener(int vibratorId, IVibratorStateListener listener) throws RemoteException;

    boolean setAlwaysOnEffect(int uid, String opPkg, int alwaysOnId, CombinedVibration vibration,
            VibrationAttributes attributes) throws RemoteException;

    void vibrate(int uid, int deviceId, String opPkg, CombinedVibration vibration, VibrationAttributes attributes,
            String reason, IBinder token) throws RemoteException;

    void cancelVibrate(int usageFilter, IBinder token) throws RemoteException;

    void performHapticFeedback(int uid, int deviceId, String opPkg, int constant, boolean always, String reason,
            boolean fromIme) throws RemoteException;

    abstract class Stub extends Binder implements IVibratorManagerService {
        public Stub(PermissionEnforcer enforcer) {
            throw new RuntimeException("Stub!");
        }

        public static IVibratorManagerService asInterface(IBinder binder) {
            throw new RuntimeException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }
    }
}
