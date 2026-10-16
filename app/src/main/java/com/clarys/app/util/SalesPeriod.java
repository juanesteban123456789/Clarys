package com.clarys.app.util;

import com.clarys.app.model.Sale;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Filtra por fecha real del registro usando la zona horaria del dispositivo. */
public final class SalesPeriod {
    private SalesPeriod() { }
    public static long timestamp(String value) {
        if (value == null || value.isEmpty()) { return 0; }
        String normalized=value.replaceFirst("\\.\\d+(?=[Z+-])","");
        if (normalized.matches(".*[+-]\\d{2}$")) { normalized += ":00"; }
        SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US);
        parser.setLenient(false);
        ParsePosition position=new ParsePosition(0);
        Date date=parser.parse(normalized,position);
        return date == null || position.getIndex()!=normalized.length() ? 0 : date.getTime();
    }

    public static List<Sale> filter(List<Sale> sales,int period,long now,TimeZone zone) {
        List<Sale> result=new ArrayList<>();
        Calendar calendar=Calendar.getInstance(zone);
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY,0); calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0); calendar.set(Calendar.MILLISECOND,0);
        calendar.add(Calendar.DAY_OF_MONTH,1);
        long end=calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_MONTH,-1);
        if (period==2) {
            int offset=(calendar.get(Calendar.DAY_OF_WEEK)+5)%7;
            calendar.add(Calendar.DAY_OF_MONTH,-offset);
        } else if (period==3) { calendar.set(Calendar.DAY_OF_MONTH,1); }
        long start=calendar.getTimeInMillis();
        for(Sale sale:sales) {
            long created=timestamp(sale.getCreatedAt());
            if(period==0 || (created>=start && created<end)) { result.add(sale); }
        }
        return result;
    }

    public static String display(String value) {
        long millis=timestamp(value);
        return millis==0 ? "Fecha no disponible"
                : new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("es","CO")).format(new Date(millis));
    }
}
