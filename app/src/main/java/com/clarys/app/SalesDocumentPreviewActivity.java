package com.clarys.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.print.PrintManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import com.clarys.app.data.SalesDocumentRepository;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.SalesDocument;
import com.clarys.app.util.SalesDocumentPdf;
import com.clarys.app.util.SalesDocumentPrintAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Vista previa nativa; exporta unicamente despues de una eleccion expresa del administrador. */
public class SalesDocumentPreviewActivity extends BaseScreenActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SupabaseStore store;
    private String workshop;
    private SalesDocument document;
    private File pdf;
    private int currentPage, pageCount;
    private boolean working;
    private final ActivityResultLauncher<String> saveFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                if (uri == null || document == null || !active()) { return; }
                working = true; renderControls();
                executor.execute(() -> {
                    try {
                        try (InputStream input = new FileInputStream(pdf);
                             OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                            if (output == null) { throw new IOException("Destino no disponible"); }
                            copy(input, output);
                        }
                        runOnUiThread(() -> {
                            if (active()) { working = false; renderControls(); showMessage(getString(R.string.documents_exported)); }
                        });
                    } catch (Exception error) { operationError(R.string.documents_export_error); }
                });
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = SupabaseStore.getInstance(this);
        if (!BuildConfig.IS_ADMIN_APP || !store.isAuthenticated()) { finish(); return; }
        workshop = store.getWorkshopId();
        setContentView(R.layout.activity_sales_document_preview);
        com.clarys.app.util.SalesDocumentUi.applyInsets(this);
        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        if (state != null) { currentPage = state.getInt("page", 0); }
        findViewById(R.id.buttonDocumentPrevious).setOnClickListener(view -> showPage(currentPage - 1));
        findViewById(R.id.buttonDocumentNext).setOnClickListener(view -> showPage(currentPage + 1));
        findViewById(R.id.buttonExportDocument).setOnClickListener(view -> saveFile.launch(fileName()));
        findViewById(R.id.buttonShareDocument).setOnClickListener(view -> share());
        findViewById(R.id.buttonPrintDocument).setOnClickListener(view -> {
            if (!active() || pdf == null) { return; }
            getSystemService(PrintManager.class).print(document.getNumber(),
                    new SalesDocumentPrintAdapter(pdf, fileName(), pageCount), null);
        });
        working = true; renderControls();
        String id = getIntent().getStringExtra("documentId");
        executor.execute(() -> {
            try {
                SalesDocumentRepository repository = new SalesDocumentRepository(getApplicationContext(), workshop);
                SalesDocument saved = repository.read(id);
                File source = repository.pdf(saved, (value, output) -> SalesDocumentPdf.write(getApplicationContext(), value, output));
                runOnUiThread(() -> {
                    if (!active()) { return; }
                    document = saved; pdf = source;
                    ((TextView) findViewById(R.id.textDocumentPreviewTitle)).setText(saved.getNumber());
                    showPage(currentPage);
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (active()) { showMessage(getString(R.string.documents_open_error)); finish(); } });
            }
        });
    }
    private boolean active() {
        return !isFinishing() && !isDestroyed() && store != null && store.isAuthenticated()
                && workshop != null && workshop.equals(store.getWorkshopId());
    }
    @Override protected void onResume() {
        super.onResume();
        if (store != null && workshop != null && !active()) { finish(); }
    }
    private String fileName() { return "Clarys_" + document.getNumber() + ".pdf"; }
    private void showPage(int requested) {
        if (pdf == null || !active()) { return; }
        working = true; renderControls();
        int width = Math.min(1200, Math.max(400, getResources().getDisplayMetrics().widthPixels));
        executor.execute(() -> {
            try (ParcelFileDescriptor input = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(input)) {
                int count = renderer.getPageCount();
                int index = Math.max(0, Math.min(requested, count - 1));
                try (PdfRenderer.Page page = renderer.openPage(index)) {
                    Bitmap bitmap = Bitmap.createBitmap(width, width * page.getHeight() / page.getWidth(), Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    runOnUiThread(() -> {
                        if (!active()) { bitmap.recycle(); return; }
                        currentPage = index; pageCount = count; working = false;
                        ((ImageView) findViewById(R.id.imageDocumentPage)).setImageBitmap(bitmap);
                        renderControls();
                    });
                }
            } catch (Exception error) { operationError(R.string.documents_open_error); }
        });
    }
    private void renderControls() {
        boolean ready = document != null && !working;
        findViewById(R.id.buttonDocumentPrevious).setEnabled(ready && currentPage > 0);
        findViewById(R.id.buttonDocumentNext).setEnabled(ready && currentPage + 1 < pageCount);
        findViewById(R.id.buttonExportDocument).setEnabled(ready);
        findViewById(R.id.buttonShareDocument).setEnabled(ready);
        findViewById(R.id.buttonPrintDocument).setEnabled(ready);
        findViewById(R.id.progressDocumentPreview).setVisibility(working ? android.view.View.VISIBLE : android.view.View.GONE);
        ((TextView) findViewById(R.id.textDocumentPageNumber)).setText(pageCount == 0 ? ""
                : getString(R.string.documents_page_number, currentPage + 1, pageCount));
    }
    private void share() {
        if (!active() || working || pdf == null) { return; }
        working = true; renderControls();
        executor.execute(() -> {
            File temporary = null;
            try {
                File directory = new File(getCacheDir(), "sales-document-shares");
                if (!directory.isDirectory() && !directory.mkdirs()) { throw new IOException("Carpeta no disponible"); }
                temporary = File.createTempFile(document.getNumber() + "-", ".pdf", directory);
                try (InputStream input = new FileInputStream(pdf); OutputStream output = new FileOutputStream(temporary)) { copy(input, output); }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".salesdocuments", temporary);
                runOnUiThread(() -> {
                    if (!active()) { return; }
                    working = false; renderControls();
                    Intent intent = new Intent(Intent.ACTION_SEND).setType("application/pdf");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.putExtra(Intent.EXTRA_TEXT, document.getType().label + " · " + document.getNumber());
                    intent.setClipData(ClipData.newRawUri(document.getNumber(), uri));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(Intent.createChooser(intent, getString(R.string.documents_share))); }
                    catch (ActivityNotFoundException error) { showMessage(getString(R.string.documents_share_error)); }
                });
            } catch (Exception error) {
                if (temporary != null) { temporary.delete(); }
                operationError(R.string.documents_share_error);
            }
        });
    }
    private void operationError(int message) {
        runOnUiThread(() -> { if (active()) { working = false; renderControls(); showMessage(getString(message)); } });
    }
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) { output.write(buffer, 0, read); }
        output.flush();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("page", currentPage); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
