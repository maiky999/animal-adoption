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
 * Konwerter JPA do szyfrowania danych tekstowych w bazie danych w celu zwiększenia bezpieczeństwa
 * Działa jak filtr/adapter, tzn. przed zapisem do bazy szyfruje tekst, a przy odczycie go deszyfruje
 */
@Component
@Converter
public class StringCryptoConverter implements AttributeConverter<String, String> {
    private static final String ALGORITHM = "AES";

    // Pobieranie klucza z konfiguracji
    @Value("${app.security.crypto-key}")
    private String secretKeyString;

    /**
     * Tu się dzieje cała magia szyfrowania, zamiana czystego tekstu na zaszyfrowany ciąg Base64
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