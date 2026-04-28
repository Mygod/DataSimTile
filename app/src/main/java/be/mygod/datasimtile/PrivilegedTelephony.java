package be.mygod.datasimtile;

import android.os.IBinder;
import android.os.Process;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class PrivilegedTelephony {
    private static final String SHELL_PACKAGE = "com.android.shell";
    private static final String SUBSCRIPTION_SERVICE = "isub";
    private static final String SUBSCRIPTION_INTERFACE = "com.android.internal.telephony.ISub";
    private static final String TELEPHONY_SERVICE = "phone";
    private static final String TELEPHONY_INTERFACE = "com.android.internal.telephony.ITelephony";
    private static final int ROOT_UID = 0;
    private static String callingPackage = SHELL_PACKAGE;

    private PrivilegedTelephony() {
    }

    static void setCallingPackage(String packageName) {
        callingPackage = Process.myUid() == 2000 || packageName == null ? SHELL_PACKAGE : packageName;
    }

    static TelephonySnapshot load() throws Exception {
        return snapshot(getSubscriptionService());
    }

    /**
     * Matches Settings' data-SIM picker: change the default data subscription, then enable user
     * mobile data on the selected subscription.
     *
     * AOSP Settings:
     * https://android.googlesource.com/platform/packages/apps/Settings/+/7c598253ff60f06f8e6fe046f18fd88e9daa72d3/src/com/android/settings/sim/SimDialogActivity.java#357
     */
    static TelephonySnapshot toggle() throws Exception {
        Object service = getSubscriptionService();
        TelephonySnapshot before = snapshot(service);
        if (!before.canSwitch()) return before;
        invoke(service, SUBSCRIPTION_INTERFACE, "setDefaultDataSubId",
                new Class<?>[] { int.class }, before.targetSubId);
        setDataEnabled(before.targetSubId);
        return snapshot(service);
    }

    private static TelephonySnapshot snapshot(Object service) throws Exception {
        int currentSubId = (Integer) invoke(service, SUBSCRIPTION_INTERFACE, "getDefaultDataSubId",
                new Class<?>[0]);
        List<SimRecord> sims = SimSelector.sortedActive(getActiveSubscriptions(service));
        SimRecord current = SimSelector.findCurrent(sims, currentSubId);
        SimRecord target = SimSelector.nextAfter(sims, currentSubId);
        return new TelephonySnapshot(TelephonySnapshot.STATUS_OK, null, currentSubId,
                current == null ? null : current.name, target == null ? -1 : target.subId,
                target == null ? null : target.name, sims.size());
    }

    private static Object getSubscriptionService() throws Exception {
        return getService(SUBSCRIPTION_SERVICE, SUBSCRIPTION_INTERFACE);
    }

    private static Object getService(String serviceName, String interfaceName) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) serviceManager.getMethod("getService", String.class)
                .invoke(null, serviceName);
        if (binder == null) throw new IllegalStateException(serviceName + " service unavailable");
        Class<?> stub = Class.forName(interfaceName + "$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private static List<SimRecord> getActiveSubscriptions(Object service) throws Exception {
        Object value;
        try {
            value = invoke(service, SUBSCRIPTION_INTERFACE, "getActiveSubscriptionInfoList",
                    new Class<?>[] { String.class, String.class, boolean.class },
                    callingPackage, null, true);
        } catch (NoSuchMethodException ignored) {
            try {
                value = invoke(service, SUBSCRIPTION_INTERFACE, "getActiveSubscriptionInfoList",
                        new Class<?>[] { String.class, String.class }, callingPackage, null);
            } catch (NoSuchMethodException ignoredAgain) {
                value = invoke(service, SUBSCRIPTION_INTERFACE, "getActiveSubscriptionInfoList",
                        new Class<?>[] { String.class }, callingPackage);
            }
        }
        ArrayList<SimRecord> result = new ArrayList<>();
        if (!(value instanceof List<?>)) return result;
        for (Object item : (List<?>) value) {
            if (!(item instanceof SubscriptionInfo info)) continue;
            result.add(new SimRecord(info.getSubscriptionId(), info.getSimSlotIndex(),
                    SimRecord.chooseName(info.getDisplayName(), info.getCarrierName(),
                            info.getSimSlotIndex())));
        }
        return result;
    }

    private static void setDataEnabled(int subId) throws Exception {
        Object service = getService(TELEPHONY_SERVICE, TELEPHONY_INTERFACE);
        try {
            invoke(service, TELEPHONY_INTERFACE, "setDataEnabledForReason",
                    new Class<?>[] { int.class, int.class, boolean.class, String.class },
                    subId, TelephonyManager.DATA_ENABLED_REASON_USER, true, dataCallingPackage());
        } catch (NoSuchMethodException ignored) {
            try {
                invoke(service, TELEPHONY_INTERFACE, "setDataEnabledForReason",
                        new Class<?>[] { int.class, int.class, boolean.class },
                        subId, TelephonyManager.DATA_ENABLED_REASON_USER, true);
            } catch (NoSuchMethodException ignoredAgain) {
                try {
                    invoke(service, TELEPHONY_INTERFACE, "setUserDataEnabled",
                            new Class<?>[] { int.class, boolean.class }, subId, true);
                } catch (NoSuchMethodException ignoredToo) {
                    invoke(service, TELEPHONY_INTERFACE, "setDataEnabled",
                            new Class<?>[] { int.class, boolean.class }, subId, true);
                }
            }
        }
    }

    private static String dataCallingPackage() {
        return Process.myUid() == ROOT_UID ? null : callingPackage;
    }

    private static Object invoke(Object service, String interfaceName, String name,
            Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = Class.forName(interfaceName).getMethod(name, parameterTypes);
        try {
            return method.invoke(service, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }
}
