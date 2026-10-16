package com.clarys.app.promotions;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.clarys.app.AppModeManager;
import com.clarys.app.BuildConfig;
import com.clarys.app.ProductPromotionActivity;
import com.clarys.app.R;
import com.clarys.app.data.SupabaseSession;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductPromotion;
import com.clarys.app.util.ProductPromotionImage;
import com.clarys.app.util.ProductSharing;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Pruebas opt-in en emulador sin red. No abre Instagram ni usa cuentas o productos reales. */
@RunWith(AndroidJUnit4.class)
public class ProductPromotionLocalTest {
    private Instrumentation instrumentation;
    private Context context;
    @Before public void isolatedOnly() {
        assumeTrue(BuildConfig.IS_ADMIN_APP && Boolean.parseBoolean(InstrumentationRegistry.getArguments()
                .getString("clarysPromotionTest", "false")));
        assertTrue("Solo emulador", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        assertEquals(1, android.provider.Settings.Global.getInt(context.getContentResolver(),
                android.provider.Settings.Global.AIRPLANE_MODE_ON, 0));
    }
    private Product product() {
        return new Product(900001, "Camiseta de algodón · edición de prueba", "", "Camisetas", 1,
                25000, 5, 1, "S, M, L", "Verde", "LOCAL_TEST", true, 0);
    }
    private Bitmap photo() {
        Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.rgb(218, 238, 230));
        Paint paint = new Paint(); paint.setColor(Color.rgb(20, 120, 90));
        canvas.drawRect(150, 80, 490, 400, paint);
        return bitmap;
    }
    private File artifact(String name) {
        File directory = context.getExternalFilesDir("promotion-local-tests");
        assertNotNull(directory); return new File(directory, name);
    }
    private void png(Bitmap bitmap, String name) throws Exception {
        try (FileOutputStream output = new FileOutputStream(artifact(name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }
    @Test public void rendersBothFormatsAndProvidesReadOnlyJpeg() throws Exception {
        Bitmap source = photo();
        Product product = product();
        product.setName("Camiseta de algodón con un nombre muy extenso y descripción con ñ para comprobar el ajuste sin superposiciones");
        ProductPromotion promotion = new ProductPromotion(product, "Taller Clarys · Confección y diseño", "$ 25.000 COP", "+573001234567");
        for (ProductPromotion.Format format : ProductPromotion.Format.values()) {
            Bitmap image = ProductPromotionImage.render(context, promotion, source, format, true, true);
            try {
                assertEquals(format.width, image.getWidth()); assertEquals(format.height, image.getHeight());
                assertNotEquals(Color.WHITE, image.getPixel(540, 500));
                png(image, format.name() + ".png");
                Uri uri = ProductSharing.prepareBitmap(context, image);
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    Bitmap decoded = BitmapFactory.decodeStream(input); assertNotNull(decoded);
                    assertEquals(format.width, decoded.getWidth()); decoded.recycle();
                }
                Intent share = ProductSharing.createShareIntent("Texto de prueba", uri);
                assertEquals("image/jpeg", share.getType());
                assertTrue((share.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
                assertEquals(0, share.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } finally { image.recycle(); }
        }
        source.recycle();
    }
    private void ready(Activity screen) {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 150 && !ready.get(); i++) {
            instrumentation.runOnMainSync(() -> ready.set(screen.findViewById(R.id.buttonPromotionSave).isEnabled()));
            if (!ready.get()) { SystemClock.sleep(100); }
        }
        assertTrue("La pieza debe generarse sin backend", ready.get());
        instrumentation.waitForIdleSync();
    }
    @SuppressWarnings("unchecked")
    @Test public void editorKeepsCustomCaptionAndExportsImage() throws Exception {
        String workshop = "promotion-local-" + UUID.randomUUID();
        SupabaseSession session = SupabaseSession.getInstance(context);
        assertTrue(session.saveAuth("LOCAL_ONLY_NOT_VALID", "LOCAL_ONLY_NOT_REFRESH", "local-demo",
                System.currentTimeMillis() / 1000 + 3600));
        session.saveProfile(workshop, "admin"); AppModeManager.enterAdminMode(context);
        Product product = product(); product.setWorkshopId(workshop);
        Bitmap photo = photo();
        File localPhoto = new File(context.getCacheDir(), "local-promotion-source.jpg");
        try (FileOutputStream output = new FileOutputStream(localPhoto)) { photo.compress(Bitmap.CompressFormat.JPEG, 90, output); }
        photo.recycle(); product.setImageUrl(Uri.fromFile(localPhoto).toString());
        // Inyectar solo en la cache del proceso de pruebas; ninguna escritura a Supabase.
        java.lang.reflect.Field field = SupabaseStore.class.getDeclaredField("products"); field.setAccessible(true);
        List<Product> products = (List<Product>) field.get(SupabaseStore.getInstance(context));
        instrumentation.runOnMainSync(() -> products.add(product));
        Activity screen = null;
        try {
            screen = instrumentation.startActivitySync(new Intent(context, ProductPromotionActivity.class)
                    .putExtra("productId", product.getId()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity activity = screen; ready(activity);
            instrumentation.runOnMainSync(() -> {
                ((CheckBox) activity.findViewById(R.id.checkPromotionPrice)).setChecked(false);
                assertFalse(((EditText) activity.findViewById(R.id.inputPromotionCaption)).getText().toString().contains("Precio:"));
            });
            ready(activity);
            instrumentation.runOnMainSync(() -> {
                ((EditText) activity.findViewById(R.id.inputPromotionCaption)).setText("Edición propia: algodón y diseño.");
                activity.findViewById(R.id.radioPromotionStory).performClick();
            });
            ready(activity);
            instrumentation.runOnMainSync(() -> assertEquals("Edición propia: algodón y diseño.",
                    ((EditText) activity.findViewById(R.id.inputPromotionCaption)).getText().toString()));
            SystemClock.sleep(500);
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot); png(screenshot, "pantalla.png"); screenshot.recycle();
            instrumentation.runOnMainSync(() -> {
                android.view.View root = ((android.view.ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
                ((android.widget.ScrollView) root).fullScroll(android.view.View.FOCUS_DOWN);
                activity.findViewById(R.id.buttonPromotionCopy).performClick();
                assertEquals("Edición propia: algodón y diseño.", context.getSystemService(android.content.ClipboardManager.class)
                        .getPrimaryClip().getItemAt(0).getText().toString());
            });
            SystemClock.sleep(500);
            Bitmap controls = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(controls); png(controls, "controles.png"); controls.recycle();
            File exported = new File(context.getCacheDir(), "shared_product_images/promotion-export-test.jpg");
            Uri target = FileProvider.getUriForFile(context, context.getPackageName() + ".productsharing", exported);
            IntentFilter filter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);
            filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addDataType("image/jpeg");
            Instrumentation.ActivityMonitor picker = instrumentation.addMonitor(filter,
                    new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(target)), true);
            try { instrumentation.runOnMainSync(() -> activity.findViewById(R.id.buttonPromotionSave).performClick()); }
            finally { instrumentation.removeMonitor(picker); }
            ready(activity);
            assertTrue(exported.length() > 0);
            Bitmap decoded = BitmapFactory.decodeFile(exported.getPath()); assertNotNull(decoded);
            assertEquals(1920, decoded.getHeight()); decoded.recycle(); exported.delete();
        } finally {
            Activity closing = screen;
            instrumentation.runOnMainSync(() -> { if (closing != null) { closing.finish(); } products.remove(product); });
            // Esperar onPause/onDestroy antes de retirar la sesion sintetica del siguiente caso.
            instrumentation.waitForIdleSync();
            session.clear(); localPhoto.delete();
        }
    }
}
