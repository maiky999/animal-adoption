package com.wsb.animaladoption.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA converter that encrypts text data in the database to improve security.
 * It works like a filter/adapter, i.e. it encrypts the text before saving it to the database and decrypts it when reading
 */
@Component
@Converter
public class StringCryptoConverter implements AttributeConverter<String, String> {
    private static final String ALGORITHM = "AES";

    // Reading the key from the configuration
    @Value("${app.security.crypto-key}")
    private String secretKeyString;

    /**
     * This is where the encryption happens: plain text is turned into an encrypted Base64 string
     */
    private byte[] getValidAesKey() {
        byte[] key = secretKeyString.getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(key, 16);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(getValidAesKey(), ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encrypted = cipher.doFinal(attribute.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Błąd podczas szyfrowania danych", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(getValidAesKey(), ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(dbData));
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("Błąd podczas deszyfrowania danych", e);
        }
    }
}