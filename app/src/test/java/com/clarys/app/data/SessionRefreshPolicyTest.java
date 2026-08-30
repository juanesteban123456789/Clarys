package com.clarys.app.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionRefreshPolicyTest {

    private static final long NOW = 10_000L;
    private static final long SAFETY_WINDOW = 120L;
    private static final long PERIODIC_INTERVAL = 900L;

    @Test
    public void refreshesBeforeAccessTokenExpires() {
        assertTrue(SessionRefreshPolicy.shouldRefresh(
                NOW,
                NOW + SAFETY_WINDOW,
                NOW - 60L,
                SAFETY_WINDOW,
                PERIODIC_INTERVAL
        ));
    }

    @Test
    public void refreshesAfterPeriodicValidationInterval() {
        assertTrue(SessionRefreshPolicy.shouldRefresh(
                NOW,
                NOW + 3_600L,
                NOW - PERIODIC_INTERVAL,
                SAFETY_WINDOW,
                PERIODIC_INTERVAL
        ));
    }

    @Test
    public void keepsRecentSessionWithoutRefreshing() {
        assertFalse(SessionRefreshPolicy.shouldRefresh(
                NOW,
                NOW + 3_600L,
                NOW - 60L,
                SAFETY_WINDOW,
                PERIODIC_INTERVAL
        ));
    }
}
