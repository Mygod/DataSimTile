package be.mygod.datasimtile;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.quicksettings.TileService;

final class TileRefreshScheduler {
    private static final int JOB_ID_DEFAULT_DATA = 1;
    private static final long TRIGGER_DELAY_MILLIS = 0;

    /**
     * Mirrors {@code Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION}.
     *
     * <p>AOSP defines the value in {@code Settings.Global} and writes it before the
     * hidden default-data-subscription broadcast:
     * https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/android/provider/Settings.java#8937
     * https://android.googlesource.com/platform/frameworks/opt/telephony/+/android-7.0.0_r1/src/java/com/android/internal/telephony/SubscriptionController.java#1491
     */
    private static final String MULTI_SIM_DATA_CALL_SUBSCRIPTION = "multi_sim_data_call";

    private TileRefreshScheduler() {
    }

    static void tileAdded(Context context) {
        TileStateStore.setTileAdded(context, true);
        setReceiverEnabled(context, true);
        schedule(context);
    }

    static void tileRemoved(Context context) {
        TileStateStore.setTileAdded(context, false);
        cancel(context);
        setReceiverEnabled(context, false);
    }

    static void wake(Context context) {
        if (TileStateStore.isTileAdded(context)) {
            schedule(context);
            requestListening(context);
        } else {
            cancel(context);
            requestListening(context);
            setReceiverEnabled(context, false);
        }
    }

    static void onJob(Context context) {
        if (TileStateStore.isTileAdded(context)) {
            schedule(context);
            requestListening(context);
        } else {
            cancel(context);
            setReceiverEnabled(context, false);
        }
    }

    private static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        scheduler.schedule(new JobInfo.Builder(JOB_ID_DEFAULT_DATA,
                new ComponentName(context, TileRefreshJobService.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        Settings.Global.getUriFor(MULTI_SIM_DATA_CALL_SUBSCRIPTION), 0))
                .setTriggerContentUpdateDelay(TRIGGER_DELAY_MILLIS)
                .setTriggerContentMaxDelay(TRIGGER_DELAY_MILLIS)
                .build());
    }

    private static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(JOB_ID_DEFAULT_DATA);
    }

    private static void requestListening(Context context) {
        TileService.requestListeningState(context,
                new ComponentName(context, DataSimTileService.class));
    }

    private static void setReceiverEnabled(Context context, boolean enabled) {
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, TileRefreshReceiver.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
