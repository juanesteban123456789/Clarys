package com.clarys.app.data;

import com.clarys.app.model.OrderRequest;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Reglas locales de avisos; no depende de Android ni contiene datos de acceso. */
public final class OrderNotificationPolicy {
    public static final int DAILY_REMINDER_HOUR = 9;

    private OrderNotificationPolicy() { }

    public static Summary summarize(String workshopId, List<OrderRequest> orders, int lastSeen) {
        Summary summary = new Summary(lastSeen);
        for (OrderRequest order : orders) {
            if (order == null || !workshopId.equals(order.getWorkshopId())) {
                continue;
            }
            summary.maximumId = Math.max(summary.maximumId, order.getId());
            if (order.isPending()) {
                summary.pending++;
                if (order.getId() > lastSeen) {
                    summary.newPending++;
                }
            } else if (order.isContacted()) {
                summary.contacted++;
            } else if (order.isApproved()) {
                summary.approved++;
            } else if (order.isPreparing()) {
                summary.preparing++;
            }
        }
        return summary;
    }

    public static String dayKey(Calendar now) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
    }

    public static boolean isDailyReminderDue(Calendar now, String lastReminderDay, int activeOrders) {
        return activeOrders > 0 && now.get(Calendar.HOUR_OF_DAY) >= DAILY_REMINDER_HOUR
                && !dayKey(now).equals(lastReminderDay);
    }

    public static final class Summary {
        private int maximumId;
        private int newPending;
        private int pending;
        private int contacted;
        private int approved;
        private int preparing;

        private Summary(int lastSeen) { maximumId = lastSeen; }

        public int getMaximumId() { return maximumId; }
        public int getNewPending() { return newPending; }
        public int getPending() { return pending; }
        public int getContacted() { return contacted; }
        public int getApproved() { return approved; }
        public int getPreparing() { return preparing; }
        public int getActiveCount() { return pending + contacted + approved + preparing; }
    }
}
