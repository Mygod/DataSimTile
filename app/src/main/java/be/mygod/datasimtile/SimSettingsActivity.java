package be.mygod.datasimtile;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/**
 * SystemUI resolves {@code TileService.ACTION_QS_TILE_PREFERENCES} on
 * <a href="https://android.googlesource.com/platform/frameworks/base/+/main/packages/SystemUI/src/com/android/systemui/qs/external/CustomTile.java#392">custom tile long press</a>.
 */
public final class SimSettingsActivity extends Activity {
    static Intent settingsIntent() {
        return new Intent(Build.VERSION.SDK_INT >= 31
                ? Settings.ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS
                : Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(settingsIntent());
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }
}
