package com.clarys.app.util;

import com.clarys.app.model.Sale;
import org.junit.Test;
import java.util.Collections;
import java.util.Arrays;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class SalesPeriodTest {
    private Sale sale(int id,String date) {
        Sale sale=new Sale(id,"Cliente","","","Confirmada",0,Collections.emptyList());
        sale.setCreatedAt(date); return sale;
    }
    @Test public void todayUsesLocalMidnightInsteadOfUtcDate() {
        long now=SalesPeriod.timestamp("2026-10-13T12:00:00Z");
        Sale previous=sale(1,"2026-10-13T02:00:00.123456+00:00");
        Sale today=sale(2,"2026-10-13T06:00:00+00");
        assertEquals(Collections.singletonList(today),
                SalesPeriod.filter(Arrays.asList(previous,today),1,now,TimeZone.getTimeZone("America/Bogota")));
    }
    @Test public void weekStartsOnMondayAndExcludesFutureDays() {
        long now=SalesPeriod.timestamp("2026-10-13T12:00:00Z");
        Sale sunday=sale(1,"2026-10-11T18:00:00Z");
        Sale monday=sale(2,"2026-10-12T18:00:00Z");
        Sale future=sale(3,"2026-10-14T18:00:00Z");
        assertEquals(Collections.singletonList(monday),SalesPeriod.filter(Arrays.asList(sunday,monday,future),
                2,now,TimeZone.getTimeZone("America/Bogota")));
    }
    @Test public void invalidDatesDoNotAppearInTodayButRemainInFullHistory() {
        Sale unknown=sale(1,"null");
        assertTrue(SalesPeriod.filter(Collections.singletonList(unknown),1,System.currentTimeMillis(),TimeZone.getDefault()).isEmpty());
        assertEquals(1,SalesPeriod.filter(Collections.singletonList(unknown),0,System.currentTimeMillis(),TimeZone.getDefault()).size());
    }
}
