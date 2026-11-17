package android.os;

public interface IVibratorStateListener extends IInterface {
    void onVibrating(boolean vibrating) throws RemoteException;

    abstract class Stub extends Binder implements IVibratorStateListener {
        public Stub() {
            throw new RuntimeException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }
    }
}
