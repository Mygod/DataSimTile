package be.mygod.datasimtile;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import androidx.annotation.NonNull;

public final class TelephonyUserService extends Binder {
    static final String DESCRIPTOR = "be.mygod.datasimtile.TelephonyUserService";
    static final int TRANSACTION_GET_STATE = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_TOGGLE = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_DESTROY = 16777115;

    public TelephonyUserService() {
    }

    public TelephonyUserService(Context context) {
        PrivilegedTelephony.setCallingPackage(context == null ? null : context.getPackageName());
    }

    @Override
    protected boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags) {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        if (code == TRANSACTION_DESTROY) {
            reply.writeNoException();
            System.exit(0);
            return true;
        }
        data.enforceInterface(DESCRIPTOR);
        TelephonySnapshot snapshot;
        try {
            if (code == TRANSACTION_GET_STATE) {
                snapshot = PrivilegedTelephony.load();
            } else if (code == TRANSACTION_TOGGLE) {
                snapshot = PrivilegedTelephony.toggle();
            } else {
                return super.onTransact(code, data, reply, flags);
            }
        } catch (Throwable throwable) {
            snapshot = TelephonySnapshot.error(throwable);
        }
        reply.writeNoException();
        snapshot.writeToParcel(reply);
        return true;
    }
}
