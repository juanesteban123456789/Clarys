package com.clarys.app.data;

import android.content.Context;
import android.content.SharedPreferences;

public class SupabaseSession {
    private static final String PREFS_NAME = "clarys_supabase_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_WORKSHOP_ID = "workshop_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_PENDING_ADMIN_EMAIL = "pending_admin_email";
    private static final String KEY_PENDING_WORKSHOP_NAME = "pending_workshop_name";
    private static final String KEY_PENDING_WORKSHOP_WHATSAPP = "pending_workshop_whatsapp";

    private static SupabaseSession instance;

    private final SharedPreferences preferences;

    private SupabaseSession(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SupabaseSession getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseSession(context);
        }
        return instance;
    }

    public void saveAuth(String accessToken, String refreshToken, String userId) {
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    public void saveProfile(String workshopId, String role) {
        preferences.edit()
                .putString(KEY_WORKSHOP_ID, workshopId)
                .putString(KEY_ROLE, role)
                .apply();
    }

    public void savePendingAdmin(String email, String workshopName, String whatsapp) {
        preferences.edit()
                .putString(KEY_PENDING_ADMIN_EMAIL, normalize(email))
                .putString(KEY_PENDING_WORKSHOP_NAME, workshopName)
                .putString(KEY_PENDING_WORKSHOP_WHATSAPP, whatsapp)
                .apply();
    }

    public boolean hasPendingAdmin(String email) {
        String pendingEmail = preferences.getString(KEY_PENDING_ADMIN_EMAIL, null);
        return pendingEmail != null && pendingEmail.equals(normalize(email));
    }

    public String getPendingWorkshopName() {
        return preferences.getString(KEY_PENDING_WORKSHOP_NAME, null);
    }

    public String getPendingWorkshopWhatsapp() {
        return preferences.getString(KEY_PENDING_WORKSHOP_WHATSAPP, null);
    }

    public void clearPendingAdmin() {
        preferences.edit()
                .remove(KEY_PENDING_ADMIN_EMAIL)
                .remove(KEY_PENDING_WORKSHOP_NAME)
                .remove(KEY_PENDING_WORKSHOP_WHATSAPP)
                .apply();
    }

    public String getAccessToken() {
        return preferences.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return preferences.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }

    public String getWorkshopId() {
        return preferences.getString(KEY_WORKSHOP_ID, null);
    }

    public String getRole() {
        return preferences.getString(KEY_ROLE, null);
    }

    public boolean isAuthenticated() {
        String token = getAccessToken();
        return token != null && !token.trim().isEmpty();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
