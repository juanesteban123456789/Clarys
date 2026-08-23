package com.clarys.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppModeManager {

    public enum AppMode {
        CLIENT,
        ADMIN
    }

    private static final String PREFS_NAME =
            "clarys_app_mode";

    private static final String KEY_MODE =
            "current_mode";


    private AppModeManager() {
        // Evitar instancias
    }


    public static AppMode getMode(Context context) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        String savedMode =
                preferences.getString(
                        KEY_MODE,
                        AppMode.CLIENT.name()
                );

        try {

            return AppMode.valueOf(savedMode);

        } catch (Exception ignored) {

            return AppMode.CLIENT;
        }
    }


    public static boolean isClientMode(Context context) {

        return getMode(context)
                == AppMode.CLIENT;
    }


    public static boolean isAdminMode(Context context) {

        return getMode(context)
                == AppMode.ADMIN;
    }


    public static void enterClientMode(Context context) {

        saveMode(
                context,
                AppMode.CLIENT
        );
    }


    public static void enterAdminMode(Context context) {

        saveMode(
                context,
                AppMode.ADMIN
        );
    }


    private static void saveMode(
            Context context,
            AppMode mode) {

        context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                        KEY_MODE,
                        mode.name()
                )
                .apply();
    }
}