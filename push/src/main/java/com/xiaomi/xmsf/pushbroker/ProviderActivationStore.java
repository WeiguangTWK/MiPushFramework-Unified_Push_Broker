package com.xiaomi.xmsf.pushbroker;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProviderActivationStore {
    private static final String PREFS_NAME = "broker_provider_state";
    private static final String KEY_PROVIDER_ENABLED = "provider_enabled";

    private ProviderActivationStore() {
    }

    public static boolean isProviderEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PROVIDER_ENABLED, false);
    }

    public static void setProviderEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PROVIDER_ENABLED, enabled).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
