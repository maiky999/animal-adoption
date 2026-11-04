package com.wsb.animaladoption.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

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
    @DisplayName("Should produce a different ciphertext for the same text each time (random IV)")
    void shouldProduceDifferentCiphertextForSameText() {
        // given
        String rawPhone = "+48 500 600 700";

        // when
        String first = converter.convertToDatabaseColumn(rawPhone);
        String second = converter.convertToDatabaseColumn(rawPhone);

        // then
        assertNotEquals(first, second, "Ten sam tekst nie może dawać tego samego szyfrogramu");
        assertEquals(rawPhone, converter.convertToEntityAttribute(first));
        assertEquals(rawPhone, converter.convertToEntityAttribute(second));
    }

    @Test
    @DisplayName("Should reject a ciphertext that was modified in the database (GCM auth tag)")
    void shouldRejectTamperedCiphertext() {
        // given
        String encrypted = converter.convertToDatabaseColumn("+48 500 600 700");
        byte[] data = Base64.getDecoder().decode(encrypted.substring("v2:".length()));
        data[data.length - 1] ^= 1; // flip one bit
        String tampered = "v2:" + Base64.getEncoder().encodeToString(data);

        // when / then
        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute(tampered));
    }

    @Test
    @DisplayName("Should still decrypt data saved in the older AES/ECB format")
    void shouldDecryptLegacyEcbData() throws Exception {
        // given - value encrypted the way the previous version did it (key cut/padded to 16 bytes, ECB)
        byte[] legacyKey = Arrays.copyOf("TestSecretKey1234567890!@#$%^&*".getBytes(StandardCharsets.UTF_8), 16);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(legacyKey, "AES"));
        String legacy = Base64.getEncoder().encodeToString(cipher.doFinal("123456789".getBytes(StandardCharsets.UTF_8)));

        // when
        String decrypted = converter.convertToEntityAttribute(legacy);

        // then
        assertEquals("123456789", decrypted);
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