package com.wsb.animaladoption.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA converter that encrypts text data in the database to improve security.
 * It works like a filter/adapter, i.e. it encrypts the text before saving it to the database and decrypts it when reading.
 * <p>
 * Algorithm: AES-256 in GCM mode with a random 12-byte IV for every value. Thanks to the random IV the same text
 * gives a different ciphertext each time, and GCM additionally detects any modification of the data (auth tag).
 * <p>
 * Stored format: {@code "v2:" + Base64(IV || ciphertext || tag)}.
 * Values without the {@code v2:} prefix come from the older version (AES/ECB) and are still decrypted,
 * so existing data keeps working - it is re-encrypted with GCM the next time the entity is updated.
 */
@Component
@Converter
public class StringCryptoConverter implements AttributeConverter<String, String> {
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String GCM_PREFIX = "v2:";
    private static final int IV_LENGTH_BYTES = 12;     // recommended IV size for GCM
    private static final int TAG_LENGTH_BITS = 128;    // length of the authentication tag

    /** Older format kept only for reading data saved before the switch to GCM. */
    private static final String LEGACY_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private static final SecureRandom RANDOM = new SecureRandom();

    // Reading the key from the configuration
    @Value("${app.security.crypto-key}")
    private String secretKeyString;

    /**
     * Derives a 256-bit AES key from the configured text using SHA-256.
     * This way the key always has the correct length, no matter how long the configured text is
     * (previously the text was cut or padded with zeros to 16 bytes).
     */
    private SecretKeySpec getKey() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secretKeyString.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare encryption key", e);
        }
    }

    /** Key used by the older version - only for decrypting legacy data. */
    private SecretKeySpec getLegacyKey() {
        byte[] key = Arrays.copyOf(secretKeyString.getBytes(StandardCharsets.UTF_8), 16);
        return new SecretKeySpec(key, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // The IV is not secret - it is stored together with the ciphertext, because it is needed for decryption
            byte[] result = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return GCM_PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Error during data encryption", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            if (dbData.startsWith(GCM_PREFIX)) {
                byte[] data = Base64.getDecoder().decode(dbData.substring(GCM_PREFIX.length()));

                Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH_BYTES));
                byte[] decrypted = cipher.doFinal(data, IV_LENGTH_BYTES, data.length - IV_LENGTH_BYTES);
                return new String(decrypted, StandardCharsets.UTF_8);
            }

            // Legacy data (AES/ECB) saved before the switch to GCM
            Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getLegacyKey());
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(dbData));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Error during data decryption", e);
        }
    }
}
