package be.mygod.datasimtile;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.StringRes;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class DataSimTileService extends TileService {
    private enum ClickState {
        IDLE,
        WAITING_FOR_BINDER,
        WAITING_FOR_PERMISSION,
        SWITCHING,
    }

    private static final String TAG = "DataSimTile";
    private static final int REQUEST_SHIZUKU_PERMISSION = 1;
    private static final long SHIZUKU_BINDER_TIMEOUT_MILLIS = 3_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DataSimTile");
        thread.setDaemon(true);
        return thread;
    });

    private ClickState clickState = ClickState.IDLE;
    private final Runnable binderTimeout = () -> {
        if (clickState != ClickState.WAITING_FOR_BINDER) return;
        clickState = ClickState.IDLE;
        openSimSettingsIfUnlocked(R.string.tile_status_shizuku_needed);
    };
    private final Shizuku.OnBinderReceivedListener binderListener = () -> {
        if (clickState == ClickState.WAITING_FOR_BINDER) {
            continueClick();
        } else {
            refreshFromShizukuIfAllowed();
        }
    };
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_SHIZUKU_PERMISSION) return;
                if (clickState == ClickState.WAITING_FOR_PERMISSION) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        clickState = ClickState.WAITING_FOR_BINDER;
                        continueClick();
                    } else {
                        clickState = ClickState.IDLE;
                        updateTileStatus(getString(R.string.tile_status_shizuku_needed),
                                Tile.STATE_INACTIVE);
                    }
                } else if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    refreshFromShizukuIfAllowed();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Shizuku.addBinderReceivedListener(binderListener);
            Shizuku.addRequestPermissionResultListener(permissionListener);
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to register Shizuku listeners", e);
        }
    }

    @Override
    public void onDestroy() {
        MAIN.removeCallbacks(binderTimeout);
        clickState = ClickState.IDLE;
        try {
            Shizuku.removeBinderReceivedListener(binderListener);
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to remove Shizuku listeners", e);
        }
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        refreshFromShizukuIfAllowed();
    }

    @Override
    public void onStartListening() {
        refreshFromShizukuIfAllowed();
    }

    @Override
    public void onClick() {
        if (clickState != ClickState.IDLE) return;
        clickState = ClickState.WAITING_FOR_BINDER;
        updateTileStatus(getString(R.string.tile_status_switching), Tile.STATE_INACTIVE);
        continueClick();
    }

    private void continueClick() {
        MAIN.removeCallbacks(binderTimeout);
        if (!Shizuku.pingBinder()) {
            clickState = ClickState.WAITING_FOR_BINDER;
            MAIN.postDelayed(binderTimeout, SHIZUKU_BINDER_TIMEOUT_MILLIS);
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (!isUserUnlocked()) {
                    clickState = ClickState.IDLE;
                    updateTileStatus(getString(R.string.tile_status_shizuku_needed),
                            Tile.STATE_INACTIVE);
                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                    clickState = ClickState.IDLE;
                    openSimSettings(R.string.tile_status_shizuku_needed);
                } else {
                    clickState = ClickState.WAITING_FOR_PERMISSION;
                    Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
                }
                return;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to check Shizuku authorization", e);
            if (!Shizuku.pingBinder()) {
                clickState = ClickState.WAITING_FOR_BINDER;
                MAIN.postDelayed(binderTimeout, SHIZUKU_BINDER_TIMEOUT_MILLIS);
            } else {
                clickState = ClickState.IDLE;
                openSimSettingsIfUnlocked(R.string.tile_status_shizuku_needed);
            }
            return;
        }
        clickState = ClickState.SWITCHING;
        WORKER.execute(() -> {
            try {
                TelephonySnapshot snapshot = PrivilegedTelephony.toggle(this);
                MAIN.post(() -> {
                    clickState = ClickState.IDLE;
                    updateTile(snapshot);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to switch the default data SIM", e);
                MAIN.post(() -> {
                    clickState = ClickState.IDLE;
                    openSimSettingsIfUnlocked(R.string.tile_status_switch_failed);
                });
            }
        });
    }

    private void refreshFromShizukuIfAllowed() {
        if (clickState != ClickState.IDLE) return;
        if (!Shizuku.pingBinder()) return;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to check Shizuku authorization while refreshing", e);
            return;
        }
        WORKER.execute(() -> {
            try {
                TelephonySnapshot snapshot = PrivilegedTelephony.load(this);
                MAIN.post(() -> {
                    if (clickState == ClickState.IDLE) updateTile(snapshot);
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to refresh the tile", e);
            }
        });
    }

    private void updateTile(TelephonySnapshot snapshot) {
        Tile tile = getQsTile();
        if (tile == null) return;
        String tileLabel = getString(R.string.tile_label);
        String currentName = displayName(snapshot.currentName(), snapshot.currentSlotIndex());
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setLabel(tileLabel);
            tile.setSubtitle(currentName);
        } else {
            tile.setLabel(currentName);
        }
        tile.setState(snapshot.canSwitch() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        String targetName = displayName(snapshot.targetName(), snapshot.targetSlotIndex());
        tile.setContentDescription(snapshot.canSwitch()
                ? getString(R.string.tile_content_description_switch, tileLabel, currentName, targetName)
                : getString(R.string.tile_content_description, tileLabel, currentName));
        tile.updateTile();
    }

    private void updateTileStatus(String status, int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
        String tileLabel = getString(R.string.tile_label);
        tile.setLabel(tileLabel);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(status);
        tile.setState(state);
        tile.setContentDescription(getString(R.string.tile_content_description, tileLabel, status));
        tile.updateTile();
    }

    private String displayName(String name, int slotIndex) {
        if (name != null && !name.isEmpty()) return name;
        return slotIndex >= 0 ? getString(R.string.sim_name, slotIndex + 1)
                : getString(R.string.unknown_sim);
    }

    private void openSimSettingsIfUnlocked(@StringRes int status) {
        if (isUserUnlocked()) {
            openSimSettings(status);
        } else {
            updateTileStatus(getString(status), Tile.STATE_INACTIVE);
        }
    }

    private boolean isUserUnlocked() {
        UserManager userManager = getSystemService(UserManager.class);
        return userManager == null || userManager.isUserUnlocked();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openSimSettings(@StringRes int status) {
        Intent intent = SimSettingsActivity.settingsIntent();
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
        updateTileStatus(getString(status), Tile.STATE_INACTIVE);
    }
}
