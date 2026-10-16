package com.clarys.app.evidence.sqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Este módulo SQLite se implementa exclusivamente con fines académicos para
 * la evidencia GA8-220501096-AA2-EV02. La autenticación productiva de Clarys
 * continúa funcionando mediante Supabase Auth.
 *
 * SQLiteOpenHelper permite mostrar de forma explícita la creación, versión y
 * actualización de una base de datos local sin introducir Room en el proyecto.
 */
public class SQLiteEvidenceDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "SQLiteEvidence";

    public static final String DATABASE_NAME = "clarys_evidence.db";
    public static final int DATABASE_VERSION = 1;

    public static final String TABLE_USERS = "evidence_users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PASSWORD_HASH = "password_hash";
    public static final String COLUMN_ACTIVE = "active";

    private static final String DEMO_EMAIL = "admin@clarys.local";
    private static final String DEMO_PASSWORD = "Sena1234*";

    /** SQL visible para la creación de la tabla solicitada en la evidencia. */
    public static final String SQL_CREATE_USERS =
            "CREATE TABLE " + TABLE_USERS + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_EMAIL + " TEXT NOT NULL UNIQUE, "
                    + COLUMN_PASSWORD_HASH + " TEXT NOT NULL, "
                    + COLUMN_ACTIVE + " INTEGER NOT NULL DEFAULT 1)";

    /** La contraseña demo se inserta únicamente después de convertirla a SHA-256. */
    private static final String SQL_INSERT_DEMO_USER =
            "INSERT OR IGNORE INTO " + TABLE_USERS
                    + " (" + COLUMN_EMAIL + ", " + COLUMN_PASSWORD_HASH + ", "
                    + COLUMN_ACTIVE + ") VALUES (?, ?, ?)";

    /**
     * Los signos ? evitan concatenar los datos escritos por el usuario dentro
     * del SQL y permiten enviarlos de forma segura mediante selectionArgs.
     */
    public static final String SQL_VALIDATE_USER =
            "SELECT " + COLUMN_ID
                    + " FROM " + TABLE_USERS
                    + " WHERE " + COLUMN_EMAIL + " = ?"
                    + " AND " + COLUMN_PASSWORD_HASH + " = ?"
                    + " AND " + COLUMN_ACTIVE + " = 1"
                    + " LIMIT 1";

    public SQLiteEvidenceDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        // La tabla pertenece solo a la demostración académica local.
        database.execSQL(SQL_CREATE_USERS);
        Log.i(TAG, "Base de datos creada");
        insertDemoUser(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Estrategia suficiente para una base académica sin información real.
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(database);
    }

    private void insertDemoUser(SQLiteDatabase database) {
        // Esta cuenta no corresponde a ningún usuario real ni a Supabase Auth.
        database.execSQL(
                SQL_INSERT_DEMO_USER,
                new Object[]{DEMO_EMAIL, hashPassword(DEMO_PASSWORD), 1});
        Log.i(TAG, "Usuario demo insertado");
    }

    /**
     * Valida las credenciales contra SQLite mediante una consulta parametrizada.
     * Nunca registra ni devuelve la contraseña o su hash.
     */
    public boolean validateUser(String email, String password) {
        if (email == null || password == null
                || email.trim().isEmpty() || password.isEmpty()) {
            Log.i(TAG, "Validación fallida");
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String passwordHash = hashPassword(password);
        SQLiteDatabase database = getReadableDatabase();

        try (Cursor cursor = database.rawQuery(
                SQL_VALIDATE_USER,
                new String[]{normalizedEmail, passwordHash})) {
            boolean validUser = cursor.moveToFirst();
            Log.i(TAG, validUser ? "Validación exitosa" : "Validación fallida");
            return validUser;
        }
    }

    /**
     * SHA-256 evita guardar la contraseña demo como texto plano. Para un sistema
     * productivo se usaría un algoritmo adaptativo con salt; aquí solo se ilustra
     * una práctica académica y Supabase continúa gestionando las cuentas reales.
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hexadecimal.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 forma parte obligatoria de Android; esta rama es defensiva.
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}
