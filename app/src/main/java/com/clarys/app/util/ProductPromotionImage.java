package com.clarys.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import com.clarys.app.R;
import com.clarys.app.model.ProductPromotion;

/** Genera un JPEG promocional local. Conserva la foto completa sin recortar la prenda. */
public final class ProductPromotionImage {
    private static final int INK = Color.rgb(23, 50, 77);
    private ProductPromotionImage() { }

    public static Bitmap render(Context context, ProductPromotion promotion, Bitmap photo,
            ProductPromotion.Format format, boolean showPrice, boolean showContact) {
        if (photo == null) { throw new IllegalArgumentException("Imagen requerida"); }
        Bitmap result = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int top = format == ProductPromotion.Format.STORY ? 180 : 50;
        paint.setColor(Color.rgb(232, 243, 248));
        canvas.drawRect(0, top - 20, 1080, top + 125, paint);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inSampleSize = 4;
        Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_taller_clarys, options);
        if (logo != null) {
            fit(canvas, logo, new RectF(50, top, 145, top + 95), paint);
            logo.recycle();
        }
        text(canvas, promotion.getWorkshop(), 175, top + 10, 845, 42, 2, true);
        int imageTop = top + 145;
        int imageBottom = format.height - (format == ProductPromotion.Format.STORY ? 545 : 415);
        fit(canvas, photo, new RectF(50, imageTop, 1030, imageBottom), paint);
        int y = imageBottom + 28;
        text(canvas, promotion.getCategory(), 60, y, 960, 28, 1, false);
        text(canvas, promotion.getName(), 60, y + 45, 960, 48, 2, true);
        y += 165;
        if (showPrice) { text(canvas, promotion.getPrice(), 60, y, 960, 48, 1, true); }
        y += 75;
        if (showContact && !promotion.getPhone().isEmpty()) {
            text(canvas, "WhatsApp: " + promotion.getPhone(), 60, y, 960, 32, 1, true);
        }
        text(canvas, "Consulta disponibilidad", 60, y + 50, 960, 26, 1, false);
        return result;
    }

    private static void fit(Canvas canvas, Bitmap bitmap, RectF area, Paint paint) {
        float scale = Math.min(area.width() / bitmap.getWidth(), area.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
        float left = area.centerX() - width / 2, top = area.centerY() - height / 2;
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + width, top + height), paint);
    }

    private static void text(Canvas canvas, String value, int x, int y, int width, int size, int lines, boolean bold) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(INK);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.create("sans-serif", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }
}
