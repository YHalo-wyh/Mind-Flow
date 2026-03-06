package com.example.mindflow.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String PREFS_NAME = "MindFlowPrefs";
    private static final String KEY_USE_BACKEND = "use_backend_inference";
    private static final String KEY_BACKEND_URL = "backend_base_url";
    private static final String DEFAULT_BACKEND_URL = "http://10.0.2.2:8000/";

    private AppSettings() {
    }

    public static boolean useBackend(Context context) {
        return getPrefs(context).getBoolean(KEY_USE_BACKEND, false);
    }

    public static void setUseBackend(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_USE_BACKEND, enabled).apply();
    }

    public static String getBackendBaseUrl(Context context) {
        String url = getPrefs(context).getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL);
        return normalizeBaseUrl(url);
    }

    public static void setBackendBaseUrl(Context context, String url) {
        getPrefs(context).edit().putString(KEY_BACKEND_URL, normalizeBaseUrl(url)).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_BACKEND_URL;
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}
