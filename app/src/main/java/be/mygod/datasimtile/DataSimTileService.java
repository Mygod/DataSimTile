package be.mygod.datasimtile;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
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
    private static final long SHIZUKU_BINDER_TIMEOUT_MILLIS = 3_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DataSimTile");
        thread.setDaemon(true);
        return thread;
    });
    private static int nextPermissionRequestCode;

    private ClickState clickState = ClickState.IDLE;
    private int permissionRequestCode;
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
                if (requestCode != permissionRequestCode ||
                        clickState != ClickState.WAITING_FOR_PERMISSION) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    clickState = ClickState.WAITING_FOR_BINDER;
                    continueClick();
                } else {
                    clickState = ClickState.IDLE;
                    updateTileStatus(getString(R.string.tile_status_shizuku_needed),
                            Tile.STATE_INACTIVE);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        Shizuku.addBinderReceivedListener(binderListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
    }

    @Override
    public void onDestroy() {
        MAIN.removeCallbacks(binderTimeout);
        clickState = ClickState.IDLE;
        Shizuku.removeBinderReceivedListener(binderListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
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
        if (clickState == ClickState.WAITING_FOR_PERMISSION) {
            // Reconcile a lost grant callback without starting another permission request.
            continueClick();
            return;
        }
        if (clickState != ClickState.IDLE) return;
        clickState = ClickState.WAITING_FOR_BINDER;
        updateTileStatus(getString(R.string.tile_status_switching), Tile.STATE_INACTIVE);
        continueClick();
    }

    private void waitForShizukuBinder() {
        clickState = ClickState.WAITING_FOR_BINDER;
        MAIN.removeCallbacks(binderTimeout);
        MAIN.postDelayed(binderTimeout, SHIZUKU_BINDER_TIMEOUT_MILLIS);
    }

    private void continueClick() {
        if (!Shizuku.pingBinder()) {
            if (clickState != ClickState.WAITING_FOR_PERMISSION) waitForShizukuBinder();
            return;
        }
        MAIN.removeCallbacks(binderTimeout);
        int shizukuPermission;
        try {
            shizukuPermission = Shizuku.checkSelfPermission();
        } catch (RuntimeException e) {
            if (clickState == ClickState.WAITING_FOR_PERMISSION) {
                if (!isExpectedShizukuAuthorizationFailure(e)) throw e;
                Log.w(TAG, "Failed to recheck Shizuku authorization", e);
            } else {
                handleShizukuAuthorizationFailure(e);
            }
            return;
        }
        if (shizukuPermission != PackageManager.PERMISSION_GRANTED) {
            if (clickState == ClickState.WAITING_FOR_PERMISSION) return;
            if (!isUserUnlocked()) {
                clickState = ClickState.IDLE;
                updateTileStatus(getString(R.string.tile_status_shizuku_needed),
                        Tile.STATE_INACTIVE);
                return;
            }
            boolean showRationale;
            try {
                showRationale = Shizuku.shouldShowRequestPermissionRationale();
            } catch (RuntimeException e) {
                handleShizukuAuthorizationFailure(e);
                return;
            }
            if (showRationale) {
                clickState = ClickState.IDLE;
                openSimSettings(R.string.tile_status_shizuku_needed);
                return;
            }
            permissionRequestCode = ++nextPermissionRequestCode;
            clickState = ClickState.WAITING_FOR_PERMISSION;
            try {
                Shizuku.requestPermission(permissionRequestCode);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Shizuku binder was not ready for the permission request", e);
                waitForShizukuBinder();
            } catch (RuntimeException e) {
                if (!isExpectedShizukuAuthorizationFailure(e)) {
                    clickState = ClickState.IDLE;
                    throw e;
                }
                Log.w(TAG, "Shizuku permission request outcome is unknown", e);
                updateTileStatus(getString(R.string.tile_status_shizuku_needed),
                        Tile.STATE_INACTIVE);
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

    private static boolean isExpectedShizukuAuthorizationFailure(RuntimeException e) {
        return e instanceof IllegalStateException || e.getCause() instanceof RemoteException;
    }

    private void handleShizukuAuthorizationFailure(RuntimeException e) {
        if (!isExpectedShizukuAuthorizationFailure(e)) throw e;
        Log.w(TAG, "Failed to use Shizuku authorization", e);
        if (!Shizuku.pingBinder()) {
            waitForShizukuBinder();
        } else {
            clickState = ClickState.IDLE;
            openSimSettingsIfUnlocked(R.string.tile_status_shizuku_needed);
        }
    }

    private void refreshFromShizukuIfAllowed() {
        if (clickState != ClickState.IDLE) return;
        if (!Shizuku.pingBinder()) return;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
        } catch (RuntimeException e) {
            if (!isExpectedShizukuAuthorizationFailure(e)) throw e;
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
