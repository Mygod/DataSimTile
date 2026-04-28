package be.mygod.datasimtile;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class DataSimTileService extends TileService {
    private static final int REQUEST_SHIZUKU_PERMISSION = 1;
    private static final String PREFS = "tile";
    private static final String KEY_CURRENT_NAME = "currentName";
    private static final String KEY_TARGET_NAME = "targetName";
    private static final String KEY_CAN_SWITCH = "canSwitch";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DataSimTile");
        thread.setDaemon(true);
        return thread;
    });

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == REQUEST_SHIZUKU_PERMISSION
                        && grantResult == PackageManager.PERMISSION_GRANTED) {
                    refreshFromShizukuIfAllowed();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (RuntimeException ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onStartListening() {
        if (!updateTileFromCache()) updateTileStatus("Shizuku needed", Tile.STATE_INACTIVE);
        refreshFromShizukuIfAllowed();
    }

    @Override
    public void onClick() {
        if (!Shizuku.pingBinder()) {
            openSimSettings();
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    openSimSettings();
                } else {
                    Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
                }
                return;
            }
        } catch (RuntimeException ignored) {
            openSimSettings();
            return;
        }
        updateTileStatus("Switching...", Tile.STATE_INACTIVE);
        WORKER.execute(() -> {
            try {
                TelephonySnapshot snapshot = ShizukuTelephonyClient.toggle(this);
                if (snapshot.hasError()) {
                    MAIN.post(this::openSimSettings);
                } else {
                    cache(snapshot);
                    MAIN.post(() -> updateTile(snapshot));
                }
            } catch (Exception ignored) {
                MAIN.post(this::openSimSettings);
            }
        });
    }

    private void refreshFromShizukuIfAllowed() {
        if (!Shizuku.pingBinder()) return;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
        } catch (RuntimeException ignored) {
            return;
        }
        WORKER.execute(() -> {
            try {
                TelephonySnapshot snapshot = ShizukuTelephonyClient.load(this);
                if (!snapshot.hasError()) {
                    cache(snapshot);
                    MAIN.post(() -> updateTile(snapshot));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private boolean updateTileFromCache() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String currentName = prefs.getString(KEY_CURRENT_NAME, null);
        String targetName = prefs.getString(KEY_TARGET_NAME, null);
        boolean canSwitch = prefs.getBoolean(KEY_CAN_SWITCH, false);
        if (currentName == null && targetName == null && !canSwitch) return false;
        updateTile(new TelephonySnapshot(TelephonySnapshot.STATUS_OK, null, -1, currentName,
                canSwitch ? 0 : -1, targetName, canSwitch ? 2 : 0));
        return true;
    }

    private void cache(TelephonySnapshot snapshot) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_CURRENT_NAME, snapshot.currentName())
                .putString(KEY_TARGET_NAME, snapshot.targetName())
                .putBoolean(KEY_CAN_SWITCH, snapshot.canSwitch())
                .apply();
    }

    private void updateTile(TelephonySnapshot snapshot) {
        Tile tile = getQsTile();
        if (tile == null) return;
        String currentName = displayName(snapshot.currentName());
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setLabel(getString(R.string.tile_label));
            tile.setSubtitle(currentName);
        } else {
            tile.setLabel(currentName);
        }
        tile.setState(snapshot.canSwitch() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        String targetName = displayName(snapshot.targetName());
        tile.setContentDescription(snapshot.canSwitch()
                ? getString(R.string.tile_label) + ", " + currentName + ", tap to switch to " + targetName
                : getString(R.string.tile_label) + ", " + currentName);
        tile.updateTile();
    }

    private void updateTileStatus(String status, int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setLabel(getString(R.string.tile_label));
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(status);
        tile.setState(state);
        tile.setContentDescription(getString(R.string.tile_label) + ", " + status);
        tile.updateTile();
    }

    private String displayName(String name) {
        return name == null || name.isEmpty() ? "Unknown SIM" : name;
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openSimSettings() {
        Intent intent = SimSettingsActivity.settingsIntent();
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
        updateTileStatus("Shizuku needed", Tile.STATE_INACTIVE);
    }
}
