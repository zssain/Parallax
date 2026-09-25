package com.parallax.application.pii;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataCipherTest {

    private final DataCipher cipher =
            new DataCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void roundTrips() {
        String plaintext = "123-45-6789";
        assertThat(cipher.decrypt(cipher.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void twoEncryptionsOfSameTextDiffer() {
        byte[] a = cipher.encrypt("same");
        byte[] b = cipher.encrypt("same");
        assertThat(Arrays.equals(a, b)).as("random IV per encryption").isFalse();
        // Both still decrypt back to the same plaintext.
        assertThat(cipher.decrypt(a)).isEqualTo(cipher.decrypt(b)).isEqualTo("same");
    }

    @Test
    void tamperedByteFailsDecryption() {
        byte[] encrypted = cipher.encrypt("secret");
        encrypted[encrypted.length - 1] ^= 0x01; // flip a bit in the ciphertext/tag
        assertThatThrownBy(() -> cipher.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void previewIsAFingerprintNotThePlaintext() {
        byte[] encrypted = cipher.encrypt("secret");
        assertThat(cipher.preview(encrypted)).matches("enc:v1:[0-9a-f]{12}…");
    }

    @Test
    void rejectsWrongLengthKey() {
        assertThatThrownBy(() -> new DataCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
