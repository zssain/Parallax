package com.parallax.application.pii;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for PII at rest (SPEC §9). Each ciphertext is {@code IV || ciphertext+tag}
 * with a fresh random 12-byte IV and a 128-bit authentication tag, so the same plaintext encrypts to
 * different bytes each time and any tampering is detected on decrypt.
 */
@Component
public class DataCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // AES-256

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(@Value("${parallax.data-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "PARALLAX_DATA_KEY (parallax.data-key) is not configured; set a base64 AES-256 key");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PARALLAX_DATA_KEY must be valid base64", e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "PARALLAX_DATA_KEY must decode to " + KEY_BYTES + " bytes (AES-256), got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Encrypt UTF-8 {@code plaintext} to {@code IV || ciphertext+tag}. */
    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Decrypt bytes produced by {@link #encrypt}; throws if the data is tampered or truncated. */
    public String decrypt(byte[] stored) {
        try {
            if (stored == null || stored.length <= IV_BYTES) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(stored, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    /** A non-reversible fingerprint of stored ciphertext: {@code "enc:v1:" + first 12 hex + "…"}. */
    public String preview(byte[] stored) {
        StringBuilder sb = new StringBuilder("enc:v1:");
        int hexChars = stored == null ? 0 : Math.min(12, stored.length * 2);
        for (int i = 0; i < hexChars; i++) {
            int b = stored[i / 2] & 0xFF;
            int nibble = (i % 2 == 0) ? (b >> 4) & 0xF : b & 0xF;
            sb.append(Character.forDigit(nibble, 16));
        }
        return sb.append("…").toString();
    }
}
