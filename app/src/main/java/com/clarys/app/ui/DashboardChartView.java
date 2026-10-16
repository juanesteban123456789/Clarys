package com.clarys.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import com.clarys.app.model.Sale;
import com.clarys.app.util.SalesPeriod;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class DashboardChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels = new String[7];
    private final long[] values = new long[7];

    public DashboardChartView(Context context) {
        super(context);
        setup();
    }

    public DashboardChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    private void setup() {
        barPaint.setColor(Color.rgb(23, 50, 77));
        textPaint.setColor(Color.rgb(92, 114, 135));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(10f*getResources().getDisplayMetrics().scaledDensity);
        gridPaint.setColor(Color.rgb(220, 229, 239));
        gridPaint.setStrokeWidth(2f);
    }

    public void setSales(List<Sale> sales) {
        Calendar start=Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY,0); start.set(Calendar.MINUTE,0);
        start.set(Calendar.SECOND,0); start.set(Calendar.MILLISECOND,0);
        start.add(Calendar.DAY_OF_MONTH,-6);
        SimpleDateFormat date=new SimpleDateFormat("dd/MM",Locale.getDefault());
        for(int index=0; index<7; index++) {
            long begin=start.getTimeInMillis();
            labels[index]=date.format(start.getTime());
            start.add(Calendar.DAY_OF_MONTH,1);
            long end=start.getTimeInMillis();
            values[index]=0;
            for(Sale sale:sales) {
                long created=SalesPeriod.timestamp(sale.getCreatedAt());
                if(created>=begin && created<end) { values[index]+=sale.getTotal(); }
            }
        }
        setContentDescription("Ventas diarias de los últimos siete días");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int top = 20;
        int bottom = height - 42;
        int chartHeight = Math.max(1, bottom - top);
        long max = 1;
        for (long value : values) {
            max = Math.max(max, value);
        }

        canvas.drawLine(18, bottom, width - 18, bottom, gridPaint);
        int slot = width / values.length;
        int barWidth = Math.max(18, slot / 3);
        for (int i = 0; i < values.length; i++) {
            float normalized = values[i] / (float) max;
            int barHeight = Math.max(values[i] == 0 ? 0 : 12, Math.round(chartHeight * normalized));
            int center = slot * i + slot / 2;
            int left = center - barWidth / 2;
            int right = center + barWidth / 2;
            int barTop = bottom - barHeight;
            canvas.drawRoundRect(left, barTop, right, bottom, 12, 12, barPaint);
            canvas.drawText(shortValue(values[i]), center, Math.max(top + 24, barTop - 8), textPaint);
            canvas.drawText(labels[i] == null ? "" : labels[i], center, height - 10, textPaint);
        }
    }

    private String shortValue(long value) {
        if (value >= 1_000_000) {
            return (value / 1_000_000) + "M";
        }
        if (value >= 1_000) {
            return (value / 1_000) + "K";
        }
        return String.valueOf(value);
    }
}
