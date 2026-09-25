package com.parallax.application.pii;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA converter that transparently encrypts a {@code String} attribute to {@code bytea} at rest and
 * decrypts it on read, via {@link DataCipher}. Spring-managed so the cipher can be injected; applied
 * explicitly on the PII columns (never auto-applied to every String).
 */
@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

    private final DataCipher cipher;

    public EncryptedStringConverter(DataCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        return dbData == null ? null : cipher.decrypt(dbData);
    }
}
