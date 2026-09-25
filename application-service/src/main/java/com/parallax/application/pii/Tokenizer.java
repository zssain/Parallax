package com.parallax.application.pii;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Deterministic, non-reversible lookup tokens for PII (SPEC §9): HMAC-SHA256 hex keyed by
 * PARALLAX_TOKEN_KEY. Same input → same token, so applications can be matched by ssn/email/phone
 * without storing the raw value. Email is lowercased and trimmed; phone is reduced to its digits.
 */
@Component
public class Tokenizer {

    private final byte[] keyBytes;

    public Tokenizer(@Value("${parallax.token-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "PARALLAX_TOKEN_KEY (parallax.token-key) is not configured; set a base64 HMAC key");
        }
        try {
            this.keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PARALLAX_TOKEN_KEY must be valid base64", e);
        }
    }

    /** HMAC of the raw SSN. */
    public String ssnToken(String ssn) {
        return hmacHex(ssn);
    }

    /** HMAC of the lowercased, trimmed email. */
    public String emailHash(String email) {
        return hmacHex(email.trim().toLowerCase());
    }

    /** HMAC of the phone's digits only. */
    public String phoneHash(String phone) {
        return hmacHex(phone.replaceAll("\\D", ""));
    }

    private String hmacHex(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }
}
