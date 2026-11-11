package com.wsb.animaladoption.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of ALTCHA - a "proof of work" CAPTCHA (https://altcha.org) that does not use any external service,
 * cookies or tracking, so it does not send users' data to third parties (GDPR friendly).
 *
 * How it works:
 * 1. The server draws a secret number N (0..MAX_NUMBER) and a random salt, and sends the browser
 *    challenge = SHA-256(salt + N) together with an HMAC signature of the challenge (so it cannot be forged).
 * 2. The widget in the browser tries numbers 0, 1, 2... until SHA-256(salt + number) equals the challenge.
 *    For a human this takes a fraction of a second, but a bot registering thousands of accounts has to pay
 *    the same CPU cost for every single form.
 * 3. The found number comes back with the form (hidden "altcha" field) and the server checks it here.
 *
 * The HMAC key is random and generated at startup - after a restart, challenges issued earlier stop being valid
 * (they are only valid for a few minutes anyway).
 */
@Slf4j
@Service
public class AltchaService {

    private static final String ALGORITHM = "SHA-256";
    /** Upper bound of the secret number - the higher it is, the longer (on average) the browser computes. */
    static final int MAX_NUMBER = 100_000;
    private static final Duration CHALLENGE_VALIDITY = Duration.ofMinutes(10);

    /** Challenge sent to the widget - the field names are defined by the ALTCHA protocol. */
    public record Challenge(String algorithm, String challenge, int maxnumber, String salt, String signature) {
    }

    private final SecureRandom random = new SecureRandom();
    private final byte[] hmacKey = new byte[32];
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    /** Challenges that have already been used (with their expiry time) - the same solution cannot be sent twice. */
    private final Map<String, Instant> usedChallenges = new ConcurrentHashMap<>();

    public AltchaService() {
        random.nextBytes(hmacKey);
    }

    public Challenge createChallenge() {
        long expires = Instant.now().plus(CHALLENGE_VALIDITY).getEpochSecond();
        // The expiry time is part of the salt, so it is covered by the hash and cannot be changed by the client
        String salt = randomHex(12) + "?expires=" + expires;
        int secretNumber = random.nextInt(MAX_NUMBER + 1);
        String challenge = sha256Hex(salt + secretNumber);
        return new Challenge(ALGORITHM, challenge, MAX_NUMBER, salt, hmacHex(challenge));
    }

    /**
     * Checks the solution sent with the form.
     *
     * @param payload value of the hidden "altcha" field: Base64-encoded JSON
     *                {algorithm, challenge, number, salt, signature}
     * @return true only for a correct, unexpired, not yet used solution of a challenge issued by this server
     */
    public boolean verify(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        try {
            JsonNode json = jsonMapper.readTree(Base64.getDecoder().decode(payload));
            String algorithm = json.path("algorithm").asString();
            String challenge = json.path("challenge").asString();
            String salt = json.path("salt").asString();
            String signature = json.path("signature").asString();
            long number = json.path("number").asLong(-1);

            Instant expiresAt = readExpiry(salt);
            boolean valid = ALGORITHM.equals(algorithm)
                    && number >= 0
                    && expiresAt != null && Instant.now().isBefore(expiresAt)
                    // the signature proves that this server issued the challenge
                    && constantTimeEquals(hmacHex(challenge), signature)
                    // the number really is the solution of the challenge
                    && constantTimeEquals(sha256Hex(salt + number), challenge);

            // putIfAbsent is atomic - when two requests send the same solution at once, only one of them passes
            return valid && usedChallenges.putIfAbsent(challenge, expiresAt) == null;
        } catch (RuntimeException e) {
            // Invalid Base64 / JSON - the payload did not come from the widget
            log.debug("Invalid ALTCHA payload", e);
            return false;
        }
    }

    /** Forgets used challenges whose validity has passed - they would be rejected as expired anyway. */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void removeExpiredChallenges() {
        Instant now = Instant.now();
        usedChallenges.values().removeIf(expiresAt -> !now.isBefore(expiresAt));
    }

    /** Reads the "expires" parameter (epoch seconds) from a salt of the form "random?expires=123". */
    private static Instant readExpiry(String salt) {
        int index = salt.indexOf("?expires=");
        if (index < 0) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(salt.substring(index + "?expires=".length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance(ALGORITHM).digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparison that takes the same time regardless of where the texts differ (no timing leaks). */
    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
