package android.os;

import android.content.AttributionSource;

public class PermissionEnforcer {
    protected PermissionEnforcer() {
        throw new RuntimeException("Stub!");
    }

    public void enforcePermission(String permission, int pid, int uid) {
        throw new RuntimeException("Stub!");
    }

    public void enforcePermission(String permission, AttributionSource source) {
        throw new RuntimeException("Stub!");
    }

    public void enforcePermissionAllOf(String[] permissions, int pid, int uid) {
        throw new RuntimeException("Stub!");
    }

    public void enforcePermissionAllOf(String[] permissions, AttributionSource source) {
        throw new RuntimeException("Stub!");
    }

    public void enforcePermissionAnyOf(String[] permissions, int pid, int uid) {
        throw new RuntimeException("Stub!");
    }

    public void enforcePermissionAnyOf(String[] permissions, AttributionSource source) {
        throw new RuntimeException("Stub!");
    }
}
