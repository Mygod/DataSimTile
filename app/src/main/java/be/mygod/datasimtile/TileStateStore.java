package be.mygod.datasimtile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

final class TileStateStore {
    static final String KEY_CURRENT_NAME = "currentName";
    static final String KEY_CURRENT_SLOT_INDEX = "currentSlotIndex";
    static final String KEY_TARGET_NAME = "targetName";
    static final String KEY_TARGET_SLOT_INDEX = "targetSlotIndex";
    static final String KEY_CAN_SWITCH = "canSwitch";

    private static final String PREFS = "tile";

    private TileStateStore() {
    }

    static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isUserUnlocked(Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        return userManager == null || userManager.isUserUnlocked();
    }

    static void migrateCacheIfUnlocked(Context context) {
        if (!isUserUnlocked(context)) return;
        SharedPreferences target = prefs(context);
        if (hasCachedTile(target)) return;
        SharedPreferences source = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!hasCachedTile(source)) return;
        SharedPreferences.Editor editor = target.edit();
        if (source.contains(KEY_CURRENT_NAME)) {
            editor.putString(KEY_CURRENT_NAME, source.getString(KEY_CURRENT_NAME, null));
        }
        if (source.contains(KEY_CURRENT_SLOT_INDEX)) {
            editor.putInt(KEY_CURRENT_SLOT_INDEX, source.getInt(KEY_CURRENT_SLOT_INDEX, -1));
        }
        if (source.contains(KEY_TARGET_NAME)) {
            editor.putString(KEY_TARGET_NAME, source.getString(KEY_TARGET_NAME, null));
        }
        if (source.contains(KEY_TARGET_SLOT_INDEX)) {
            editor.putInt(KEY_TARGET_SLOT_INDEX, source.getInt(KEY_TARGET_SLOT_INDEX, -1));
        }
        if (source.contains(KEY_CAN_SWITCH)) {
            editor.putBoolean(KEY_CAN_SWITCH, source.getBoolean(KEY_CAN_SWITCH, false));
        }
        editor.apply();
    }

    private static boolean hasCachedTile(SharedPreferences prefs) {
        return prefs.contains(KEY_CURRENT_NAME) || prefs.contains(KEY_CURRENT_SLOT_INDEX)
                || prefs.contains(KEY_TARGET_NAME) || prefs.contains(KEY_TARGET_SLOT_INDEX)
                || prefs.contains(KEY_CAN_SWITCH);
    }
}
