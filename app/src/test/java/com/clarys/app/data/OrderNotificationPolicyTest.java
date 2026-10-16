package com.clarys.app.data;

import com.clarys.app.model.OrderRequest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class OrderNotificationPolicyTest {
    private OrderRequest order(int id, String workshop, String status) {
        return new OrderRequest(id, workshop, "Cliente demo", "", Collections.emptyList(),
                status, "", "", "", "", "", "", "", "");
    }

    private Calendar time(int day, int hour) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
        calendar.clear();
        calendar.set(2026, Calendar.OCTOBER, day, hour, 0);
        return calendar;
    }

    @Test public void summaryIncludesAllUnfinishedStatusesButNotTerminalOrders() {
        OrderNotificationPolicy.Summary summary = OrderNotificationPolicy.summarize("a", Arrays.asList(
                order(1, "a", OrderRequest.STATUS_PENDING), order(2, "a", OrderRequest.STATUS_CONTACTED),
                order(3, "a", OrderRequest.STATUS_APPROVED), order(4, "a", OrderRequest.STATUS_PREPARING),
                order(5, "a", OrderRequest.STATUS_COMPLETED), order(6, "a", OrderRequest.STATUS_REJECTED)), 0);
        assertEquals(4, summary.getActiveCount());
        assertEquals(1, summary.getPending());
        assertEquals(1, summary.getContacted());
        assertEquals(1, summary.getApproved());
        assertEquals(1, summary.getPreparing());
        assertEquals(1, summary.getNewPending());
        assertEquals(6, summary.getMaximumId());
    }

    @Test public void summaryDoesNotMixWorkshopsOrRepeatKnownOrders() {
        OrderNotificationPolicy.Summary summary = OrderNotificationPolicy.summarize("a", Arrays.asList(
                order(2, "a", OrderRequest.STATUS_PENDING), order(3, "a", OrderRequest.STATUS_PENDING),
                order(99, "b", OrderRequest.STATUS_PENDING)), 2);
        assertEquals(2, summary.getActiveCount());
        assertEquals(1, summary.getNewPending());
        assertEquals(3, summary.getMaximumId());
    }

    @Test public void dailyReminderWaitsUntilNineAndRunsOncePerLocalDay() {
        assertFalse(OrderNotificationPolicy.isDailyReminderDue(time(14, 8), "", 2));
        assertTrue(OrderNotificationPolicy.isDailyReminderDue(time(14, 9), "", 2));
        assertFalse(OrderNotificationPolicy.isDailyReminderDue(time(14, 18), "2026-10-14", 2));
        assertTrue(OrderNotificationPolicy.isDailyReminderDue(time(15, 9), "2026-10-14", 2));
    }

    @Test public void noReminderWithoutOutstandingOrders() {
        assertFalse(OrderNotificationPolicy.isDailyReminderDue(time(14, 10), "", 0));
        assertEquals(0, OrderNotificationPolicy.summarize("a", Collections.emptyList(), 12).getActiveCount());
        assertEquals(12, OrderNotificationPolicy.summarize("a", Collections.emptyList(), 12).getMaximumId());
    }

    @Test public void dateUsesTheDeviceTimeZoneAcrossMidnight() {
        Calendar bogota = time(14, 20);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(bogota.getTimeInMillis());
        assertEquals("2026-10-14", OrderNotificationPolicy.dayKey(bogota));
        assertEquals("2026-10-15", OrderNotificationPolicy.dayKey(utc));
    }
}
