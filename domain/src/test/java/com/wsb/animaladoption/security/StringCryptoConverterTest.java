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
        // Test key for AES-128 / AES-256 (16 or 32 characters)
        ReflectionTestUtils.setField(converter, "secretKeyString", "TestSecretKey1234567890!@#$%^&*");
    }

    @Test
    @DisplayName("Should correctly encrypt and decrypt a phone number (round-trip)")
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
    @DisplayName("Should return null when null is passed for encryption")
    void shouldReturnNullWhenConvertingNullToDatabaseColumn() {
        // when
        String encrypted = converter.convertToDatabaseColumn(null);

        // then
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should return null when null is passed for decryption")
    void shouldReturnNullWhenConvertingNullToEntityAttribute() {
        // when
        String decrypted = converter.convertToEntityAttribute(null);

        // then
        assertNull(decrypted);
    }
}