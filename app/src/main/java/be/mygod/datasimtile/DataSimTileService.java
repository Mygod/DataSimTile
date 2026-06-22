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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class DataSimTileService extends TileService {
    private static final int REQUEST_SHIZUKU_PERMISSION = 1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DataSimTile");
        thread.setDaemon(true);
        return thread;
    });

    private final Shizuku.OnBinderReceivedListener binderListener = this::refreshFromShizukuIfAllowed;
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
            Shizuku.addBinderReceivedListener(binderListener);
            Shizuku.addRequestPermissionResultListener(permissionListener);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderListener);
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (RuntimeException ignored) {
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
        if (!Shizuku.pingBinder()) {
            openSimSettingsIfUnlocked();
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (!isUserUnlocked()) {
                    updateTileStatus(getString(R.string.tile_status_shizuku_needed),
                            Tile.STATE_INACTIVE);
                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                    openSimSettings();
                } else {
                    Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
                }
                return;
            }
        } catch (RuntimeException ignored) {
            openSimSettingsIfUnlocked();
            return;
        }
        updateTileStatus(getString(R.string.tile_status_switching), Tile.STATE_INACTIVE);
        WORKER.execute(() -> {
            try {
                TelephonySnapshot snapshot = PrivilegedTelephony.toggle(this);
                MAIN.post(() -> updateTile(snapshot));
            } catch (Exception ignored) {
                MAIN.post(this::openSimSettingsIfUnlocked);
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
                TelephonySnapshot snapshot = PrivilegedTelephony.load(this);
                MAIN.post(() -> updateTile(snapshot));
            } catch (Exception ignored) {
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

    private void openSimSettingsIfUnlocked() {
        if (isUserUnlocked()) {
            openSimSettings();
        } else {
            updateTileStatus(getString(R.string.tile_status_shizuku_needed), Tile.STATE_INACTIVE);
        }
    }

    private boolean isUserUnlocked() {
        UserManager userManager = getSystemService(UserManager.class);
        return userManager == null || userManager.isUserUnlocked();
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
        updateTileStatus(getString(R.string.tile_status_shizuku_needed), Tile.STATE_INACTIVE);
    }
}
