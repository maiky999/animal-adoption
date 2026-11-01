package com.wsb.animaladoption.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class StringCryptoConverterTest {

    private StringCryptoConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringCryptoConverter();
        // Klucz testowy dla AES-128 / AES-256 (16 lub 32 znaki)
        ReflectionTestUtils.setField(converter, "secretKeyString", "TestSecretKey1234567890!@#$%^&*");
    }

    @Test
    @DisplayName("Powinien poprawnie zaszyfrować i odszyfrować numer telefonu (round-trip)")
    void shouldEncryptAndDecryptSuccessfully() {
        // given
        String rawPhone = "+48 500 600 700";

        // when
        String encrypted = converter.convertToDatabaseColumn(rawPhone);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        // then
        assertNotNull(encrypted);
        assertNotEquals(rawPhone, encrypted, "Zaszyfrowany tekst nie może być tożsamy z jawnym");
        assertEquals(rawPhone, decrypted, "Odszyfrowany tekst musi być identyczny z oryginałem");
    }

    @Test
    @DisplayName("Powinien zwrócić null, gdy do zaszyfrowania przekazano null")
    void shouldReturnNullWhenConvertingNullToDatabaseColumn() {
        // when
        String encrypted = converter.convertToDatabaseColumn(null);

        // then
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Powinien zwrócić null, gdy do odszyfrowania przekazano null")
    void shouldReturnNullWhenConvertingNullToEntityAttribute() {
        // when
        String decrypted = converter.convertToEntityAttribute(null);

        // then
        assertNull(decrypted);
    }
}