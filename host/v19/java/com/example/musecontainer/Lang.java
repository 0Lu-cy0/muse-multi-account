package com.example.musecontainer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import java.util.Locale;

/**
 * v11: central language helper for the HOST UI only. The guest app
 * (the user-supplied APK) is untouched; its language is its own.
 *
 * - Choice persisted in SharedPreferences "app_settings", key
 *   "app_language", values "vi" | "en". Default "vi", matching the
 *   user's language and the UI's primary language before v11.
 * - Activities and services wrap their base context with wrap()
 *   (attachBaseContext) so getString()/layout inflation resolve
 *   strings.xml (vi, default) or values-en/strings.xml (en).
 * - Toggling saves the choice and the activity recreates, so every
 *   string refreshes at once. The choice survives app restarts
 *   because it is read from SharedPreferences on every attach.
 * - No behaviour change: this class only selects a locale.
 */
public final class Lang {
    public static final String PREFS = "app_settings";
    public static final String KEY = "app_language";
    public static final String VI = "vi";
    public static final String EN = "en";

    private Lang() {}

    public static String getLanguage(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String v = p.getString(KEY, VI);
            return EN.equals(v) ? EN : VI;
        } catch (Exception e) {
            return VI;
        }
    }

    public static boolean isEnglish(Context ctx) {
        return EN.equals(getLanguage(ctx));
    }

    public static void setLanguage(Context ctx, String lang) {
        String v = EN.equals(lang) ? EN : VI;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, v).apply();
    }

    /** The label the toggle button shows is always the OTHER
     *  language, so tapping it switches to what it says. */
    public static String toggleLabel(Context ctx) {
        return isEnglish(ctx) ? "Ti\u1ebfng Vi\u1ec7t" : "English";
    }

    public static Context wrap(Context base) {
        try {
            String lang = getLanguage(base);
            Locale locale = new Locale(lang);
            Locale.setDefault(locale);
            Configuration config = new Configuration(base.getResources().getConfiguration());
            if (Build.VERSION.SDK_INT >= 17) {
                config.setLocale(locale);
            } else {
                config.locale = locale;
            }
            return base.createConfigurationContext(config);
        } catch (Exception e) {
            return base;
        }
    }
}
