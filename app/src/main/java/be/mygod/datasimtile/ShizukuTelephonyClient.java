package be.mygod.datasimtile;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rikka.shizuku.Shizuku;

final class ShizukuTelephonyClient {
    private static final Object LOCK = new Object();
    private static IBinder service;

    private ShizukuTelephonyClient() {
    }

    static TelephonySnapshot load(Context context) throws Exception {
        return transact(context, TelephonyUserService.TRANSACTION_GET_STATE);
    }

    static TelephonySnapshot toggle(Context context) throws Exception {
        return transact(context, TelephonyUserService.TRANSACTION_TOGGLE);
    }

    private static TelephonySnapshot transact(Context context, int code) throws Exception {
        IBinder binder = requireService(context.getApplicationContext());
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(TelephonyUserService.DESCRIPTOR);
            if (!binder.transact(code, data, reply, 0)) {
                throw new IllegalStateException("telephony service transaction failed");
            }
            reply.readException();
            return TelephonySnapshot.readFromParcel(reply);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static IBinder requireService(Context context) throws Exception {
        synchronized (LOCK) {
            if (service != null && service.pingBinder()) return service;
            service = null;
        }
        if (!Shizuku.pingBinder()) throw new IllegalStateException("Shizuku is not running");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IBinder> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                result.set(binder);
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                synchronized (LOCK) {
                    if (service == result.get()) service = null;
                }
            }

            @Override
            public void onBindingDied(ComponentName name) {
                failure.set(new IllegalStateException("telephony user service binding died"));
                latch.countDown();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                failure.set(new IllegalStateException("telephony user service returned null binding"));
                latch.countDown();
            }
        };
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(context, TelephonyUserService.class))
                .processNameSuffix("telephony")
                .tag("telephony")
                .version(BuildConfig.VERSION_CODE)
                .daemon(false);
        Shizuku.bindUserService(args, connection);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out binding telephony user service");
        }
        RuntimeException exception = failure.get();
        if (exception != null) throw exception;
        IBinder binder = result.get();
        if (binder == null) throw new IllegalStateException("telephony user service unavailable");
        synchronized (LOCK) {
            service = binder;
        }
        return binder;
    }
}
