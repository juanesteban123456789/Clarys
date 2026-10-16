package com.clarys.app.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.core.content.IntentCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;

import static org.junit.Assert.*;

/** Comprueba el contrato de compartir sin enviar mensajes ni consultar el backend. */
@RunWith(AndroidJUnit4.class)
public class ProductSharingTest {
    @Test
    public void imageIsReadableAndSharedTogetherWithText() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        Uri image;
        try {
            image = ProductSharing.cacheImage(context, bitmap);
        } finally {
            bitmap.recycle();
        }
        try {
            assertEquals("content", image.getScheme());
            assertEquals(context.getPackageName() + ".productsharing", image.getAuthority());
            assertEquals("image/jpeg", context.getContentResolver().getType(image));
            try (InputStream input = context.getContentResolver().openInputStream(image)) {
                assertNotNull(input);
                assertEquals(0xff, input.read());
                assertEquals(0xd8, input.read());
            }
            Intent intent = ProductSharing.createShareIntent("Producto · precio", image);
            assertEquals(Intent.ACTION_SEND, intent.getAction());
            assertEquals("image/jpeg", intent.getType());
            assertEquals("Producto · precio", intent.getStringExtra(Intent.EXTRA_TEXT));
            assertEquals(image, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class));
            assertEquals(image, intent.getClipData().getItemAt(0).getUri());
            assertNotEquals(0, intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            assertEquals(0, intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } finally {
            context.getContentResolver().delete(image, null, null);
        }
    }

    @Test
    public void missingImageKeepsTextOnlySharing() {
        Intent intent = ProductSharing.createShareIntent("Texto del producto", null);
        assertEquals("text/plain", intent.getType());
        assertEquals("Texto del producto", intent.getStringExtra(Intent.EXTRA_TEXT));
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM));
        assertNull(intent.getClipData());
    }

    @Test(expected = IllegalArgumentException.class)
    public void providerDoesNotExposeOtherPrivateFiles() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FileProvider.getUriForFile(context, context.getPackageName() + ".productsharing",
                new File(context.getFilesDir(), "private-session.json"));
    }
}
