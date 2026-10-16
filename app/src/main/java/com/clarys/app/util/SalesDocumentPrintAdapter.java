package com.clarys.app.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Imprime solo las paginas seleccionadas; nunca cambia el PDF original. */
public final class SalesDocumentPrintAdapter extends PrintDocumentAdapter {
    private final File file;
    private final String name;
    private final int pageCount;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    public SalesDocumentPrintAdapter(File file, String name, int pageCount) {
        this.file = file; this.name = name; this.pageCount = pageCount;
    }
    @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                   CancellationSignal signal, LayoutResultCallback callback, Bundle extras) {
        if (signal.isCanceled()) { callback.onLayoutCancelled(); return; }
        callback.onLayoutFinished(new PrintDocumentInfo.Builder(name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pageCount).build(), true);
    }
    @Override public void onWrite(PageRange[] ranges, ParcelFileDescriptor destination,
                                  CancellationSignal signal, WriteResultCallback callback) {
        executor.execute(() -> {
            boolean written;
            try (FileOutputStream stream = new FileOutputStream(destination.getFileDescriptor())) {
                written = writeSelectedPages(file, stream, ranges, signal);
            } catch (Exception exception) { callback.onWriteFailed("No se pudo preparar la impresión"); return; }
            if (written) { callback.onWriteFinished(ranges); }
            else { callback.onWriteCancelled(); }
        });
    }
    public static boolean writeSelectedPages(File file, OutputStream stream, PageRange[] ranges,
                                              CancellationSignal signal) throws IOException {
        PdfDocument output = new PdfDocument();
        try (ParcelFileDescriptor input = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(input)) {
                for (int index = 0; index < renderer.getPageCount(); index++) {
                    if (signal.isCanceled()) { return false; }
                    boolean selected = false;
                    for (PageRange range : ranges) { selected |= index >= range.getStart() && index <= range.getEnd(); }
                    if (!selected) { continue; }
                    try (PdfRenderer.Page original = renderer.openPage(index)) {
                        Bitmap bitmap = Bitmap.createBitmap(original.getWidth() * 2, original.getHeight() * 2, Bitmap.Config.ARGB_8888);
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE);
                            original.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            PdfDocument.Page page = output.startPage(new PdfDocument.PageInfo.Builder(
                                    original.getWidth(), original.getHeight(), index + 1).create());
                            Canvas canvas = page.getCanvas();
                            canvas.scale(0.5f, 0.5f);
                            canvas.drawBitmap(bitmap, 0, 0, null);
                            output.finishPage(page);
                        } finally { bitmap.recycle(); }
                    }
                }
                output.writeTo(stream);
                return true;
        } finally { output.close(); }
    }
    @Override public void onFinish() { executor.shutdown(); }
}
