package com.clarys.app.data;

import android.content.Context;
import android.util.AtomicFile;
import com.clarys.app.model.SalesDocument;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Historial privado de esta instalacion, aislado por taller. No modifica Supabase ni inventario. */
public final class SalesDocumentRepository {
    private final File directory;
    private final String workshopId;
    private final SessionCipher cipher = new SessionCipher();
    @FunctionalInterface public interface PdfWriter { void write(SalesDocument document, OutputStream output) throws IOException; }

    public SalesDocumentRepository(Context context, String workshopId) throws IOException {
        if (workshopId == null || workshopId.isEmpty()) { throw new IOException("Acceso administrativo requerido"); }
        this.workshopId = workshopId;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(workshopId.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte value : hash) { name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
            directory = new File(context.getFilesDir(), "sales-documents/" + name);
            if (!directory.isDirectory() && !directory.mkdirs()) { throw new IOException("No se pudo abrir el historial local"); }
        } catch (GeneralSecurityException exception) { throw new IOException("No se pudo proteger el historial", exception); }
    }
    private File file(String id, String extension) {
        return new File(directory, UUID.fromString(id).toString() + extension);
    }
    public synchronized List<SalesDocument> list() throws IOException {
        List<SalesDocument> documents = new ArrayList<>();
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".enc"));
        if (files == null) { throw new IOException("No se pudo leer el historial local"); }
        for (File file : files) { documents.add(read(file.getName().substring(0, file.getName().length() - 4))); }
        documents.sort(Comparator.comparingLong(SalesDocument::getCreatedAt).reversed());
        return documents;
    }
    public synchronized SalesDocument read(String id) throws IOException {
        try {
            String stored = new String(new AtomicFile(file(id, ".enc")).readFully(), StandardCharsets.UTF_8);
            SalesDocument document = SalesDocumentCodec.decode(cipher.decrypt(stored));
            if (!workshopId.equals(document.getWorkshopId()) || !id.equals(document.getId())) {
                throw new IOException("El documento no pertenece a este taller");
            }
            return document;
        } catch (org.json.JSONException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new IOException("No se pudo leer el documento guardado; no se eliminó", exception);
        }
    }
    public synchronized SalesDocument findSource(SalesDocument.Type type, String source) throws IOException {
        if (source == null || source.isEmpty()) { return null; }
        for (SalesDocument document : list()) {
            if (document.getType() == type && source.equals(document.getSourceKey())) { return document; }
        }
        return null;
    }
    public SalesDocument save(SalesDocument document, PdfWriter writer) throws IOException {
        // Distintas pantallas pueden usar instancias distintas del repositorio al mismo tiempo.
        synchronized (SalesDocumentRepository.class) { return saveLocked(document, writer); }
    }
    private SalesDocument saveLocked(SalesDocument document, PdfWriter writer) throws IOException {
        if (!workshopId.equals(document.getWorkshopId())) { throw new IOException("Taller incorrecto"); }
        if (file(document.getId(), ".enc").exists()) { return read(document.getId()); }
        SalesDocument existing = findSource(document.getType(), document.getSourceKey());
        if (existing != null) { return existing; }
        AtomicFile metadata = new AtomicFile(file(document.getId(), ".enc"));
        FileOutputStream output = null;
        try {
            pdf(document, writer);
            String encrypted = cipher.encrypt(SalesDocumentCodec.encode(document));
            output = metadata.startWrite();
            output.write(encrypted.getBytes(StandardCharsets.UTF_8));
            metadata.finishWrite(output);
            return document;
        } catch (Exception exception) {
            if (output != null) { metadata.failWrite(output); }
            new AtomicFile(file(document.getId(), ".pdf")).delete();
            throw new IOException("No se pudo guardar el documento", exception);
        }
    }
    public synchronized File pdf(SalesDocument document, PdfWriter writer) throws IOException {
        if (!workshopId.equals(document.getWorkshopId())) { throw new IOException("Taller incorrecto"); }
        File pdf = file(document.getId(), ".pdf");
        if (pdf.isFile() && pdf.length() > 0) { return pdf; }
        AtomicFile atomic = new AtomicFile(pdf);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            writer.write(document, output);
            atomic.finishWrite(output);
            return pdf;
        } catch (Exception exception) {
            if (output != null) { atomic.failWrite(output); }
            throw new IOException("No se pudo generar el PDF", exception);
        }
    }
    public synchronized void delete(String id) throws IOException {
        read(id);
        new AtomicFile(file(id, ".enc")).delete();
        new AtomicFile(file(id, ".pdf")).delete();
        if (file(id, ".enc").exists()) { throw new IOException("No se pudo eliminar el documento local"); }
    }
}
