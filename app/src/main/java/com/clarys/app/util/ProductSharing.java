package com.clarys.app.util;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Comparte fotos del catalogo mediante archivos temporales, sin permisos de almacenamiento. */
public final class ProductSharing {
    private static final int IMAGE_MAX_SIZE = 1200;
    private static final int JPEG_QUALITY = 90;
    private static final long IMAGE_TIMEOUT_SECONDS = 30;
    private static final long CACHE_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final String CACHE_DIRECTORY = "shared_product_images";

    private ProductSharing() { }

    /** Reutiliza el proveedor acotado para una pieza promocional creada localmente. */
    public static Uri prepareBitmap(Context context, Bitmap bitmap) throws IOException {
        return cacheImage(context.getApplicationContext(), bitmap);
    }

    /** Debe ejecutarse fuera del hilo de interfaz; aprovecha la cache existente de Glide. */
    public static Uri prepareImage(Context context, String imageUrl)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Context application = context.getApplicationContext();
        FutureTarget<Bitmap> target = Glide.with(application).asBitmap()
                .load(imageUrl).fitCenter().disallowHardwareConfig()
                .submit(IMAGE_MAX_SIZE, IMAGE_MAX_SIZE);
        try {
            return cacheImage(application, target.get(IMAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            Glide.with(application).clear(target);
        }
    }

    static Uri cacheImage(Context context, Bitmap bitmap) throws IOException {
        File directory = new File(context.getCacheDir(), CACHE_DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("No se pudo preparar la carpeta temporal de imagenes.");
        }
        File[] previousImages = directory.listFiles();
        long expiration = System.currentTimeMillis() - CACHE_RETENTION_MILLIS;
        if (previousImages != null) {
            for (File previous : previousImages) {
                if (previous.isFile() && previous.getName().startsWith("product-")
                        && previous.getName().endsWith(".jpg") && previous.lastModified() < expiration) {
                    previous.delete();
                }
            }
        }
        // Un archivo diferente por envio evita cambiar fotos de compartidos aun pendientes.
        File image = File.createTempFile("product-", ".jpg", directory);
        boolean complete = false;
        try {
            try (FileOutputStream output = new FileOutputStream(image)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw new IOException("No se pudo preparar la imagen del producto.");
                }
            }
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".productsharing", image);
            complete = true;
            return uri;
        } finally {
            if (!complete) {
                image.delete();
            }
        }
    }

    /** Solo concede lectura de la foto adjunta, nunca acceso general al almacenamiento privado. */
    public static Intent createShareIntent(String text, Uri image) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        if (image == null) {
            intent.setType("text/plain");
        } else {
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM, image);
            intent.setClipData(ClipData.newRawUri("Imagen del producto", image));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent;
    }
}
