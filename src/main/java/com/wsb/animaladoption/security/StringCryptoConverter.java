package com.wsb.animaladoption.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Konwerter JPA do szyfrowania danych tekstowych w bazie danych w celu zwiększenia bezpieczeństwa
 * Działa jak filtr/adapter, tzn. przed zapisem do bazy szyfruje tekst, a przy odczycie go deszyfruje
 * Dzięki temu w bazie PostgreSQL dane takie jak np. numer telefonu i wiadomości są nie do przeczytania
 */
@Converter
public class StringCryptoConverter implements AttributeConverter<String, String> {
    private static final String ALGORITHM = "AES";
    private static final byte[] KEY = "TajnyKluczDoBazy".getBytes();

    /**
     * Tu się dzieje cała magia szyfrowania, zamiana czystego tekstu na zaszyfrowany ciąg Base64
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            // inicjalizacja tej maszyny szyfrującej
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            // w tym miejscu szyfruje tekst i daje tablice bajtów
            byte[] encrypted = cipher.doFinal(attribute.getBytes());
            // tu zamienia na Base64, bo baza danych nie lubi surowych bajtów
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Błąd podczas szyfrowania danych", e);
        }
    }

    /**
     * Deszyfrowanie, odpala się automatycznie przy pobieraniu danych z bazy, np. przez repo
     * Zamienia nieczytelny szyfr z bazy z powrotem na czysty tekst dla naszej apki
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        try {
            // inicjalizacja maszyny szyfrującej w tryb deszyfrowania
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            // najpierw odkodowujemy tekst z formatu Base64 z powrotem do bajtów, a potem je deszyfrujemy
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(dbData));
            // zamienia odkodowane bajty na zwykłego Stringa
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("Błąd podczas deszyfrowania danych", e);
        }
    }
}
