package com.clarys.app.data;

/**
 * Define cuándo una sesión debe renovarse según su vencimiento y la última
 * validación realizada contra Supabase.
 */
final class SessionRefreshPolicy {

    private SessionRefreshPolicy() {
    }

    static boolean shouldRefresh(long nowEpochSeconds,
                                 long expiresAtEpochSeconds,
                                 long lastRefreshAtEpochSeconds,
                                 long safetyWindowSeconds,
                                 long periodicIntervalSeconds) {
        return expiresAtEpochSeconds <= 0L
                || nowEpochSeconds + safetyWindowSeconds >= expiresAtEpochSeconds
                || lastRefreshAtEpochSeconds <= 0L
                || nowEpochSeconds - lastRefreshAtEpochSeconds >= periodicIntervalSeconds;
    }
}
