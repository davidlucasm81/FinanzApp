package com.finanzapp.app.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUtils {
    private static final String PREF_NAME = "finanzapp_prefs";
    private static final String KEY_PRIVACY_MODE = "privacy_mode_enabled";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isPrivacyModeEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PRIVACY_MODE, false);
    }

    public static void setPrivacyModeEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply();
    }
}
