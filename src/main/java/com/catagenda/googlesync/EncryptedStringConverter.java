package com.catagenda.googlesync;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * T1.7 — a JPA {@code AttributeConverter} is the standard extension point
 * for "store this Java type as something else in the database, transparent
 * to every other layer": Hibernate calls {@link #convertToDatabaseColumn}
 * and {@link #convertToEntityAttribute} automatically on every write/read
 * of the fields it's {@code @Convert}-annotated onto (see
 * {@link GoogleCalendarConnection#getAccessToken()}), so entity code, the
 * repository, and the service layer all keep working with plain
 * {@code String} while the database column actually stores AES-GCM
 * ciphertext, Base64-encoded. This is the first {@code AttributeConverter}
 * in this codebase.
 * <p>
 * Refresh tokens are long-lived, high-value credentials — equivalent to a
 * password for the user's calendar (ADR 0008's Consequences) — so plaintext
 * storage was never on the table, even at this app's current scale.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecretKey key = resolveKey();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // IV is not secret -- it's stored alongside the ciphertext (prepended)
            // so decryption can recover it later without a separate column.
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token column", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token column", e);
        }
    }

    /**
     * Reads a Base64-encoded 256-bit AES key from {@code GOOGLE_TOKEN_ENCRYPTION_KEY}
     * (never committed). Falls back to a randomly-generated in-process key
     * when unset, so the app still boots for local dev/tests with no Google
     * configuration at all — tokens encrypted with that ephemeral key
     * simply won't decrypt across a restart, which is harmless since
     * there's no real OAuth connection to preserve in that case anyway. A
     * real deployment must set this env var; a full secrets-manager story
     * is explicitly out of scope for this phase (ADR 0008's Consequences).
     */
    private static SecretKey resolveKey() {
        String configured = System.getenv("GOOGLE_TOKEN_ENCRYPTION_KEY");
        byte[] keyBytes;
        if (configured != null && !configured.isBlank()) {
            keyBytes = Base64.getDecoder().decode(configured);
        } else {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
