package com.clarys.app.documents;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.print.PageRange;
import android.print.PrintDocumentAdapter;
import android.widget.EditText;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.clarys.app.AppModeManager;
import com.clarys.app.BuildConfig;
import com.clarys.app.R;
import com.clarys.app.SalesDocumentFormActivity;
import com.clarys.app.SalesDocumentPreviewActivity;
import com.clarys.app.data.SalesDocumentRepository;
import com.clarys.app.data.SupabaseSession;
import com.clarys.app.model.SalesDocument;
import com.clarys.app.util.SalesDocumentPdf;
import com.clarys.app.util.SalesDocumentPrintAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/** Solo emulador aislado y opt-in. Ninguna prueba requiere Supabase ni cuentas reales. */
@RunWith(AndroidJUnit4.class)
public class SalesDocumentsLocalTest {
    private Context context;
    private Instrumentation instrumentation;
    @Before public void localOnly() {
        assumeTrue(BuildConfig.IS_ADMIN_APP && Boolean.parseBoolean(InstrumentationRegistry.getArguments()
                .getString("clarysLocalPdfTest", "false")));
        assertTrue("Esta prueba esta limitada a emuladores locales", "ranchu".equals(Build.HARDWARE)
                || "goldfish".equals(Build.HARDWARE));
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        assertEquals("El emulador debe permanecer sin red", 1, android.provider.Settings.Global.getInt(
                context.getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 0));
    }
    private SalesDocument.Builder draft(String workshop) {
        return new SalesDocument.Builder(workshop, "Taller Clarys · Confección y diseño", "+573000000000", "COP")
                .customer("María Núñez", "+573001112233", "Demo 123", "demo@example.invalid")
                .items(Collections.singletonList(new SalesDocument.Line("Camiseta algodón · talla M · azul", 2, 12000)))
                .discount(2000).notes("Documento de demostración local. Entrega acordada con el cliente.");
    }
    private SalesDocumentRepository.PdfWriter writer() {
        return (document, output) -> SalesDocumentPdf.write(context, document, output);
    }
    private File artifact(String name) {
        File directory = context.getExternalFilesDir("pdf-local-tests");
        assertNotNull(directory); return new File(directory, name);
    }
    private void copy(File source, File target) throws Exception {
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) { output.write(buffer, 0, read); }
        }
    }
    private byte[] hash(File file) throws Exception {
        java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) { hash.update(buffer, 0, read); }
        }
        return hash.digest();
    }
    private void saveButton(Activity form) {
        androidx.test.espresso.Espresso.closeSoftKeyboard();
        onView(withId(R.id.buttonSaveDocument)).perform(centerScroll())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                        androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
        // Esta prueba valida el formulario; no depende de coordenadas afectadas por el IME.
        instrumentation.runOnMainSync(() -> {
            assertTrue(form.findViewById(R.id.buttonSaveDocument).isEnabled());
            assertTrue(form.findViewById(R.id.buttonSaveDocument).performClick());
        });
    }
    private androidx.test.espresso.ViewAction centerScroll() {
        return new androidx.test.espresso.ViewAction() {
            @Override public org.hamcrest.Matcher<android.view.View> getConstraints() {
                return androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA(
                        androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.ScrollView.class));
            }
            @Override public String getDescription() { return "Centrar vista respetando insets de Android 16"; }
            @Override public void perform(androidx.test.espresso.UiController ui, android.view.View view) {
                android.view.View ancestor = (android.view.View) view.getParent();
                while (!(ancestor instanceof android.widget.ScrollView)) { ancestor = (android.view.View) ancestor.getParent(); }
                android.widget.ScrollView scroll = (android.widget.ScrollView) ancestor;
                int[] target = new int[2], container = new int[2];
                view.getLocationOnScreen(target); scroll.getLocationOnScreen(container);
                int space = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
                scroll.scrollBy(0, target[1] - container[1] - scroll.getPaddingTop() - (space - view.getHeight()) / 2);
                ui.loopMainThreadForAtLeast(250);
            }
        };
    }
    @Test public void repositoryPreservesSnapshotsAndSeparatesWorkshops() throws Exception {
        String workshop = "local-pdf-" + UUID.randomUUID();
        SalesDocumentRepository repository = new SalesDocumentRepository(context, workshop);
        SalesDocument document = draft(workshop).source("order:7", "Pedido #7").build();
        SalesDocument saved = repository.save(document, writer());
        File pdf = repository.pdf(saved, writer()); byte[] initial = hash(pdf);
        assertEquals(22000, repository.read(saved.getId()).getTotal());
        assertEquals("María Núñez", repository.read(saved.getId()).getCustomerName());
        SalesDocument retry = draft(workshop).source("order:7", "Pedido #7").discount(1000).build();
        assertEquals(saved.getId(), new SalesDocumentRepository(context, workshop).save(retry, writer()).getId());
        assertEquals(1, repository.list().size()); assertArrayEquals(initial, hash(pdf));
        SalesDocumentRepository other = new SalesDocumentRepository(context, "other-" + workshop);
        assertTrue(other.list().isEmpty());
        try { other.read(saved.getId()); fail("No debe leerse otro taller"); } catch (java.io.IOException expected) { }
        try { repository.read("../session"); fail("No debe aceptarse una ruta"); } catch (java.io.IOException expected) { }
        copy(pdf, artifact("comprobante.pdf"));
        try (ParcelFileDescriptor input = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(input)) { assertEquals(1, renderer.getPageCount()); }
        try {
            FileProvider.getUriForFile(context, context.getPackageName() + ".salesdocuments", pdf);
            fail("El proveedor no debe exponer el historial privado");
        } catch (IllegalArgumentException expected) { }
        repository.delete(saved.getId()); assertTrue(repository.list().isEmpty());
    }
    @Test public void failedPdfDoesNotLeaveAnIssuedDocument() throws Exception {
        String workshop = "local-failed-" + UUID.randomUUID();
        SalesDocumentRepository repository = new SalesDocumentRepository(context, workshop);
        SalesDocument document = draft(workshop).build();
        try { repository.save(document, (value, output) -> { output.write(1); throw new java.io.IOException("Prueba"); }); fail(); }
        catch (java.io.IOException expected) { }
        assertTrue(repository.list().isEmpty());
        assertEquals(document.getId(), repository.save(document, writer()).getId());
        repository.delete(document.getId());
    }
    @Test public void longDocumentsPaginateRenderAndPrintOnlyTheSelectedPage() throws Exception {
        String workshop = "local-pages-" + UUID.randomUUID();
        List<SalesDocument.Line> lines = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            lines.add(new SalesDocument.Line("Prenda " + (index + 1) + " · Pantalón de confección, talla XXL, color azul petróleo. "
                    + "Detalle con ñ y acentos para comprobar el ajuste del texto.", 2, 12500));
        }
        SalesDocument document = draft(workshop).type(SalesDocument.Type.QUOTE).items(lines).discount(5000).build();
        File pdf = artifact("cotizacion_multipagina.pdf");
        try (FileOutputStream output = new FileOutputStream(pdf)) { SalesDocumentPdf.write(context, document, output); }
        int pages;
        try (ParcelFileDescriptor input = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(input)) {
            pages = renderer.getPageCount(); assertTrue(pages >= 3);
            for (int index : new int[]{0, pages - 1}) {
                try (PdfRenderer.Page page = renderer.openPage(index)) {
                    Bitmap bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(android.graphics.Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    int nonWhite = 0;
                    for (int y = 0; y < 842; y += 3) { for (int x = 0; x < 595; x += 3) {
                        if ((bitmap.getPixel(x, y) & 0xffffff) != 0xffffff) { nonWhite++; }
                    } }
                    assertTrue("La pagina no puede estar en blanco", nonWhite > 300);
                    try (FileOutputStream output = new FileOutputStream(artifact(index == 0 ? "multipagina_primera.png" : "multipagina_ultima.png"))) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    } finally { bitmap.recycle(); }
                }
            }
        }
        File printed = artifact("pagina_seleccionada.pdf");
        try (FileOutputStream output = new FileOutputStream(printed)) {
            assertTrue(SalesDocumentPrintAdapter.writeSelectedPages(pdf, output,
                    new PageRange[]{new PageRange(1, 1)}, new CancellationSignal()));
        }
        try (ParcelFileDescriptor input = ParcelFileDescriptor.open(printed, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(input)) { assertEquals(1, renderer.getPageCount()); }
    }
    @Test public void manualFormValidatesAndExportsWithoutAnyBackend() throws Exception {
        String workshop = "local-form-" + UUID.randomUUID();
        SupabaseSession session = SupabaseSession.getInstance(context);
        assertTrue(session.saveAuth("LOCAL_TEST_NOT_A_VALID_TOKEN", "LOCAL_TEST_NOT_A_REFRESH_TOKEN", "local-user", System.currentTimeMillis() / 1000 + 3600));
        session.saveProfile(workshop, "admin"); AppModeManager.enterAdminMode(context);
        Activity form = instrumentation.startActivitySync(new Intent(context, SalesDocumentFormActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity preview = null;
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(SalesDocumentPreviewActivity.class.getName(), null, false);
        try {
            androidx.test.espresso.Espresso.closeSoftKeyboard();
            saveButton(form);
            assertNotNull(((EditText) form.findViewById(R.id.inputDocumentCustomer)).getError());
            onView(withId(R.id.inputDocumentCustomer)).perform(centerScroll(), replaceText("María Núñez"), closeSoftKeyboard());
            onView(withId(R.id.buttonAddDocumentLine)).perform(centerScroll(), click());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withId(R.id.inputDocumentLineDescription)).perform(replaceText("Camiseta de algodón · talla M"));
            onView(withId(R.id.inputDocumentLineQuantity)).perform(replaceText("0"));
            onView(withId(R.id.inputDocumentLinePrice)).perform(replaceText("12000"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withId(R.id.inputDocumentLineQuantity)).perform(replaceText("2"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withId(R.id.inputDocumentDiscount)).perform(centerScroll(), replaceText("25000"), closeSoftKeyboard());
            saveButton(form);
            assertNotNull(((EditText) form.findViewById(R.id.inputDocumentDiscount)).getError());
            onView(withId(R.id.inputDocumentDiscount)).perform(centerScroll(), replaceText("2000"), closeSoftKeyboard());
            onView(withId(R.id.inputDocumentNotes)).perform(centerScroll(), replaceText("Entrega el sábado. Documento de prueba."), closeSoftKeyboard());
            saveButton(form);
            preview = monitor.waitForActivityWithTimeout(15000); assertNotNull(preview);
            Activity finalPreview = preview; AtomicBoolean ready = new AtomicBoolean();
            for (int index = 0; index < 100 && !ready.get(); index++) {
                instrumentation.runOnMainSync(() -> ready.set(finalPreview.findViewById(R.id.buttonExportDocument).isEnabled()));
                if (!ready.get()) { SystemClock.sleep(100); }
            }
            assertTrue("La vista previa debe cargar el PDF", ready.get());
            List<SalesDocument> saved = new SalesDocumentRepository(context, workshop).list();
            assertEquals(1, saved.size()); assertEquals(22000, saved.get(0).getTotal());
            assertEquals(SalesDocument.Payment.UNVERIFIED, saved.get(0).getPayment());
            instrumentation.waitForIdleSync();
            SystemClock.sleep(500);
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try (FileOutputStream output = new FileOutputStream(artifact("vista_previa.png"))) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally { screenshot.recycle(); }

            File exported = new File(context.getCacheDir(), "sales-document-shares/export-local.pdf");
            assertTrue(exported.getParentFile().isDirectory() || exported.getParentFile().mkdirs());
            android.net.Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".salesdocuments", exported);
            IntentFilter filter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);
            filter.addCategory(Intent.CATEGORY_OPENABLE);
            filter.addDataType("application/pdf");
            Instrumentation.ActivityMonitor picker = instrumentation.addMonitor(filter,
                    new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(uri)), true);
            try { onView(withId(R.id.buttonExportDocument)).perform(click()); }
            finally { instrumentation.removeMonitor(picker); }
            for (int index = 0; index < 100 && exported.length() == 0; index++) { SystemClock.sleep(100); }
            assertTrue("Debe exportarse un archivo PDF", exported.length() > 0);
            assertArrayEquals(hash(new SalesDocumentRepository(context, workshop).pdf(saved.get(0), writer())), hash(exported));
            exported.delete();
            new SalesDocumentRepository(context, workshop).delete(saved.get(0).getId());
        } catch (RuntimeException error) {
            Bitmap failure = instrumentation.getUiAutomation().takeScreenshot();
            if (failure != null) {
                try (FileOutputStream output = new FileOutputStream(artifact("form-failure.png"))) {
                    failure.compress(Bitmap.CompressFormat.PNG, 100, output);
                } finally { failure.recycle(); }
            }
            throw error;
        } finally {
            instrumentation.removeMonitor(monitor);
            Activity closingPreview = preview;
            instrumentation.runOnMainSync(() -> { if (closingPreview != null) { closingPreview.finish(); } form.finish(); });
            session.clear();
        }
    }
}
