package com.clarys.app.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cifra los tokens locales con una clave AES que nunca sale de Android Keystore.
 */
final class SessionCipher {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String VALUE_PREFIX = "enc:v1:";
    private static final String KEY_ALIAS = "clarys_supabase_session_v1";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    String encrypt(String plainText) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());

        String initializationVector = Base64.encodeToString(
                cipher.getIV(),
                Base64.NO_WRAP
        );
        String encryptedValue = Base64.encodeToString(
                cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP
        );
        return VALUE_PREFIX + initializationVector + ":" + encryptedValue;
    }

    String decrypt(String storedValue) throws GeneralSecurityException {
        if (!isEncrypted(storedValue)) {
            throw new GeneralSecurityException("Formato de sesión no cifrado");
        }

        String[] components = storedValue.substring(VALUE_PREFIX.length()).split(":", 2);
        if (components.length != 2) {
            throw new GeneralSecurityException("Formato de sesión inválido");
        }

        byte[] initializationVector = Base64.decode(components[0], Base64.NO_WRAP);
        byte[] encryptedValue = Base64.decode(components[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector)
        );
        return new String(cipher.doFinal(encryptedValue), StandardCharsets.UTF_8);
    }

    boolean isEncrypted(String value) {
        return value != null && value.startsWith(VALUE_PREFIX);
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        try {
            keyStore.load(null);
        } catch (Exception exception) {
            throw new GeneralSecurityException("No se pudo abrir Android Keystore", exception);
        }

        KeyStore.Entry existingEntry = keyStore.getEntry(KEY_ALIAS, null);
        if (existingEntry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existingEntry).getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return keyGenerator.generateKey();
    }
}
