package com.parallax.application.json;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The single canonical JSON serializer used for hashing (SPEC §5, §14): rule config_hash now and
 * ledger hashes in Prompt 08. Properties are sorted alphabetically, map entries ordered by key,
 * no indentation, and temporal values written as ISO-8601 strings (never epoch numbers) so the same
 * object always produces byte-identical JSON. This is the ONLY serializer allowed to feed a hash.
 *
 * <p>Also usable outside Spring (the Flyway Java migration constructs one with {@code new}).
 */
@Component
public class CanonicalJson {

    private final ObjectMapper mapper;

    public CanonicalJson() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Serialize to canonical JSON (sorted keys, no whitespace, ISO dates). */
    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to write canonical JSON for " + value.getClass(), e);
        }
    }

    /** Deserialize JSON text into the given type. */
    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read " + type.getSimpleName() + " from JSON", e);
        }
    }

    /** Lowercase hex SHA-256 of the UTF-8 bytes of {@code text} (64 characters). */
    public String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
