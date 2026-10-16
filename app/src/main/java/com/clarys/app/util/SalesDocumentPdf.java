package com.clarys.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import com.clarys.app.R;
import com.clarys.app.model.SalesDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** PDF A4 paginado. Solo representa la copia guardada del documento comercial. */
public final class SalesDocumentPdf {
    private static final int WIDTH = 595, HEIGHT = 842, MARGIN = 36, CONTENT_BOTTOM = 770;
    private final PdfDocument pdf = new PdfDocument();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SalesDocument document;
    private final Bitmap logo;
    private PdfDocument.Page page;
    private Canvas canvas;
    private float y;
    private int pageNumber;

    private SalesDocumentPdf(Context context, SalesDocument document) {
        this.document = document;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_taller_clarys, options);
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 256) { options.inSampleSize *= 2; }
        options.inJustDecodeBounds = false;
        logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_taller_clarys, options);
    }
    public static String money(long value, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        format.setCurrency(Currency.getInstance(currency));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        return format.format(value) + " " + currency;
    }
    public static void write(Context context, SalesDocument document, OutputStream output) throws IOException {
        SalesDocumentPdf writer = new SalesDocumentPdf(context, document);
        try {
            writer.draw();
            writer.finishPage();
            writer.pdf.writeTo(output);
        } finally {
            if (writer.page != null) { writer.pdf.finishPage(writer.page); }
            writer.pdf.close();
            if (writer.logo != null) { writer.logo.recycle(); }
        }
    }
    private void font(float size, boolean bold, int color) {
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setColor(color);
    }
    private List<String> wrap(String text, float width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.replace("\r", "").split("\n", -1)) {
            String remaining = paragraph.trim();
            if (remaining.isEmpty()) { lines.add(""); }
            while (!remaining.isEmpty()) {
                int count = paint.breakText(remaining, true, width, null);
                if (count == 0) { count = 1; }
                if (count < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', count - 1);
                    if (space > 0) { count = space; }
                }
                lines.add(remaining.substring(0, count));
                remaining = remaining.substring(count).trim();
            }
        }
        return lines;
    }
    private void paragraph(String text, float size, boolean bold) {
        font(size, bold, 0xff17324d);
        List<String> lines = wrap(text, WIDTH - 2 * MARGIN);
        for (String line : lines) {
            if (y + size + 6 > CONTENT_BOTTOM) { newPage(false); }
            font(size, bold, 0xff17324d);
            canvas.drawText(line, MARGIN, y + size, paint);
            y += size + 6;
        }
    }
    private void newPage(boolean table) {
        finishPage();
        page = pdf.startPage(new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++pageNumber).create());
        canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        if (logo != null) {
            float height = Math.min(60, 70f * logo.getHeight() / logo.getWidth());
            float width = height * logo.getWidth() / logo.getHeight();
            canvas.drawBitmap(logo, null, new RectF(MARGIN, 25, MARGIN + width, 25 + height), paint);
        }
        font(15, true, 0xff17324d);
        List<String> business = wrap(document.getBusinessName(), WIDTH - MARGIN - 125);
        float top = 42;
        for (String line : business) { canvas.drawText(line, 125, top, paint); top += 18; }
        font(10, false, 0xff476780);
        canvas.drawText(document.getNumber(), 125, top + 2, paint);
        paint.setColor(0xffc9dce8);
        canvas.drawLine(MARGIN, 105, WIDTH - MARGIN, 105, paint);
        y = 118;
        if (table) { tableHeader(); }
    }
    private void finishPage() {
        if (page == null) { return; }
        font(9, false, 0xff476780);
        canvas.drawText(SalesDocument.DISCLAIMER, MARGIN, 797, paint);
        canvas.drawText("Documento comercial · Clarys", MARGIN, 813, paint);
        String number = "Página " + pageNumber;
        canvas.drawText(number, WIDTH - MARGIN - paint.measureText(number), 813, paint);
        pdf.finishPage(page);
        page = null;
    }
    private void tableHeader() {
        paint.setColor(0xffe9f2f8);
        canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + 26, paint);
        font(10, true, 0xff17324d);
        canvas.drawText("Concepto", MARGIN + 6, y + 17, paint);
        right("Cant.", 345, y + 17);
        right("Precio", 447, y + 17);
        right("Importe", WIDTH - MARGIN - 6, y + 17);
        y += 32;
    }
    private void right(String text, float edge, float baseline) {
        canvas.drawText(text, edge - paint.measureText(text), baseline, paint);
    }
    private void draw() {
        newPage(false);
        paragraph(document.getType().label, 20, true);
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("es", "CO"));
        date.setTimeZone(java.util.TimeZone.getTimeZone("America/Bogota"));
        paragraph(date.format(new Date(document.getCreatedAt())) + " · Colombia", 10, false);
        if (!document.getBusinessPhone().isEmpty()) { paragraph("WhatsApp del taller: " + document.getBusinessPhone(), 10, false); }
        y += 8;
        paragraph("Cliente: " + document.getCustomerName(), 12, true);
        if (!document.getCustomerId().isEmpty()) { paragraph("Identificación: " + document.getCustomerId(), 10, false); }
        if (!document.getCustomerPhone().isEmpty()) { paragraph("Teléfono: " + document.getCustomerPhone(), 10, false); }
        if (!document.getCustomerEmail().isEmpty()) { paragraph("Correo: " + document.getCustomerEmail(), 10, false); }
        paragraph("Origen: " + document.getSourceLabel(), 10, false);
        paragraph(document.getType() == SalesDocument.Type.QUOTE ? "Cotización · No acredita pago"
                : document.getPayment().label, 10, true);
        y += 12;
        if (y + 120 > CONTENT_BOTTOM) { newPage(false); }
        tableHeader();
        for (SalesDocument.Line item : document.getItems()) {
            font(11, false, 0xff17324d);
            List<String> lines = wrap(item.getDescription(), 265);
            float height = Math.max(34, lines.size() * 15 + 16);
            if (y + height > CONTENT_BOTTOM) { newPage(true); }
            font(11, false, 0xff17324d);
            float baseline = y + 13;
            for (String line : lines) { canvas.drawText(line, MARGIN + 6, baseline, paint); baseline += 15; }
            font(10, false, 0xff17324d);
            right(String.valueOf(item.getQuantity()), 345, y + 13);
            drawAmount(item.getUnitPrice(), 350, 447, y + 13);
            drawAmount(item.getSubtotal(), 453, WIDTH - MARGIN - 6, y + 13);
            y += height;
            paint.setColor(0xffdce6ed);
            canvas.drawLine(MARGIN, y - 5, WIDTH - MARGIN, y - 5, paint);
        }
        if (y + 110 > CONTENT_BOTTOM) { newPage(false); }
        y += 15;
        total("Subtotal", document.getSubtotal(), false);
        total("Descuento", document.getDiscount(), false);
        total("Total", document.getTotal(), true);
        if (!document.getNotes().isEmpty()) {
            y += 16;
            paragraph("Observaciones", 11, true);
            paragraph(document.getNotes(), 10, false);
        }
    }
    private void drawAmount(long amount, float left, float right, float baseline) {
        String text = money(amount, document.getCurrency());
        font(10, false, 0xff17324d);
        if (paint.measureText(text) > right - left) { paint.setTextSize(10 * (right - left) / paint.measureText(text)); }
        right(text, right, baseline);
    }
    private void total(String label, long amount, boolean bold) {
        font(bold ? 14 : 11, bold, 0xff17324d);
        canvas.drawText(label, 300, y + 13, paint);
        right(money(amount, document.getCurrency()), WIDTH - MARGIN, y + 13);
        y += bold ? 29 : 24;
    }
}
