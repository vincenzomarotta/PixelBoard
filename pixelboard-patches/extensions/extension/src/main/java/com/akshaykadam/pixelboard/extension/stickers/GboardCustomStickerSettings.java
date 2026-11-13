/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.stickers;

import android.content.Context;
import android.content.SharedPreferences;

import com.akshaykadam.pixelboard.extension.settings.GboardPatchesSettings;

/**
 * Persistent preferences for PixelBoard Custom Stickers & GIFs.
 */
public final class GboardCustomStickerSettings {
    public static final String PREF_KEY_ENABLED = "custom_stickers_enabled";
    public static final String PREF_KEY_SHOW_IN_FEATURES_MENU = "custom_stickers_show_in_features_menu";
    public static final String PREF_KEY_AUTO_REMOVE_BG = "custom_stickers_auto_remove_bg";
    public static final String PREF_KEY_OUTLINE_ENABLED = "custom_stickers_outline_enabled";
    public static final String PREF_KEY_BG_TOLERANCE = "custom_stickers_bg_tolerance";
    public static final String PREF_KEY_COPY_TO_CLIPBOARD_ON_SEND = "custom_stickers_copy_to_clipboard";

    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_SHOW_IN_FEATURES_MENU = true;
    public static final boolean DEFAULT_AUTO_REMOVE_BG = true;
    public static final boolean DEFAULT_OUTLINE_ENABLED = true;
    public static final int DEFAULT_BG_TOLERANCE = 32;
    public static final boolean DEFAULT_COPY_TO_CLIPBOARD_ON_SEND = true;

    public static final int TOLERANCE_LOW = 18;
    public static final int TOLERANCE_MEDIUM = 32;
    public static final int TOLERANCE_HIGH = 48;
    public static final int TOLERANCE_AGGRESSIVE = 64;

    private static volatile Boolean cachedEnabled;
    private static volatile Boolean cachedShowInFeaturesMenu;

    private GboardCustomStickerSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        return GboardPatchesSettings.preferences(context);
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = null;
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            editor = preferences.edit().putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_SHOW_IN_FEATURES_MENU)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_SHOW_IN_FEATURES_MENU, DEFAULT_SHOW_IN_FEATURES_MENU);
        }
        if (!preferences.contains(PREF_KEY_AUTO_REMOVE_BG)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_AUTO_REMOVE_BG, DEFAULT_AUTO_REMOVE_BG);
        }
        if (!preferences.contains(PREF_KEY_OUTLINE_ENABLED)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_OUTLINE_ENABLED, DEFAULT_OUTLINE_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_BG_TOLERANCE)) {
            if (editor == null) editor = preferences.edit();
            editor.putInt(PREF_KEY_BG_TOLERANCE, DEFAULT_BG_TOLERANCE);
        }
        if (!preferences.contains(PREF_KEY_COPY_TO_CLIPBOARD_ON_SEND)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_COPY_TO_CLIPBOARD_ON_SEND, DEFAULT_COPY_TO_CLIPBOARD_ON_SEND);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ENABLED;
        }
        return preferences.getBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static boolean isEnabled(Context context) {
        Boolean cached = cachedEnabled;
        if (cached != null) {
            return cached.booleanValue();
        }
        if (context == null) {
            return DEFAULT_ENABLED;
        }
        try {
            boolean enabled = readEnabled(preferences(context));
            cachedEnabled = Boolean.valueOf(enabled);
            return enabled;
        } catch (Throwable ignored) {
            return DEFAULT_ENABLED;
        }
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        cachedEnabled = Boolean.valueOf(enabled);
        return preferences.edit().putBoolean(PREF_KEY_ENABLED, enabled).commit();
    }

    public static boolean readShowInFeaturesMenu(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_SHOW_IN_FEATURES_MENU;
        }
        return preferences.getBoolean(PREF_KEY_SHOW_IN_FEATURES_MENU, DEFAULT_SHOW_IN_FEATURES_MENU);
    }

    public static boolean isShowInFeaturesMenuEnabled(Context context) {
        Boolean cached = cachedShowInFeaturesMenu;
        if (cached != null) {
            return cached.booleanValue();
        }
        if (context == null) {
            return DEFAULT_SHOW_IN_FEATURES_MENU;
        }
        try {
            boolean enabled = readShowInFeaturesMenu(preferences(context));
            cachedShowInFeaturesMenu = Boolean.valueOf(enabled);
            return enabled;
        } catch (Throwable ignored) {
            return DEFAULT_SHOW_IN_FEATURES_MENU;
        }
    }

    public static boolean writeShowInFeaturesMenu(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        cachedShowInFeaturesMenu = Boolean.valueOf(enabled);
        return preferences.edit().putBoolean(PREF_KEY_SHOW_IN_FEATURES_MENU, enabled).commit();
    }

    public static boolean readAutoRemoveBg(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_AUTO_REMOVE_BG;
        }
        return preferences.getBoolean(PREF_KEY_AUTO_REMOVE_BG, DEFAULT_AUTO_REMOVE_BG);
    }

    public static boolean writeAutoRemoveBg(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit().putBoolean(PREF_KEY_AUTO_REMOVE_BG, enabled).commit();
    }

    public static boolean readOutlineEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_OUTLINE_ENABLED;
        }
        return preferences.getBoolean(PREF_KEY_OUTLINE_ENABLED, DEFAULT_OUTLINE_ENABLED);
    }

    public static boolean writeOutlineEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit().putBoolean(PREF_KEY_OUTLINE_ENABLED, enabled).commit();
    }

    public static int readBgTolerance(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_BG_TOLERANCE;
        }
        int val = preferences.getInt(PREF_KEY_BG_TOLERANCE, DEFAULT_BG_TOLERANCE);
        return Math.max(0, Math.min(100, val));
    }

    public static boolean writeBgTolerance(SharedPreferences preferences, int tolerance) {
        if (preferences == null) {
            return false;
        }
        int clamped = Math.max(0, Math.min(100, tolerance));
        return preferences.edit().putInt(PREF_KEY_BG_TOLERANCE, clamped).commit();
    }

    public static boolean readCopyToClipboardOnSend(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_COPY_TO_CLIPBOARD_ON_SEND;
        }
        return preferences.getBoolean(PREF_KEY_COPY_TO_CLIPBOARD_ON_SEND, DEFAULT_COPY_TO_CLIPBOARD_ON_SEND);
    }

    public static boolean writeCopyToClipboardOnSend(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit().putBoolean(PREF_KEY_COPY_TO_CLIPBOARD_ON_SEND, enabled).commit();
    }

    public static String formatToleranceLabel(int tolerance) {
        if (tolerance <= 20) {
            return "Low (" + tolerance + "%)";
        }
        if (tolerance <= 38) {
            return "Medium (" + tolerance + "%)";
        }
        if (tolerance <= 54) {
            return "High (" + tolerance + "%)";
        }
        return "Aggressive (" + tolerance + "%)";
    }

    public static void invalidateCache() {
        cachedEnabled = null;
        cachedShowInFeaturesMenu = null;
    }
}
