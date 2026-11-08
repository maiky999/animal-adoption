package com.wsb.animaladoption.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AltchaServiceTest {

    private final AltchaService altchaService = new AltchaService();

    /** Does what the widget does in the browser: tries numbers until the hash matches the challenge. */
    private static long solve(AltchaService.Challenge challenge) {
        for (long number = 0; number <= challenge.maxnumber(); number++) {
            if (AltchaService.sha256Hex(challenge.salt() + number).equals(challenge.challenge())) {
                return number;
            }
        }
        throw new AssertionError("No solution found");
    }

    /** Builds the value of the hidden "altcha" field in the same format as the widget (Base64 JSON). */
    private static String payload(AltchaService.Challenge challenge, long number, String signature) {
        String json = """
                {"algorithm":"%s","challenge":"%s","number":%d,"salt":"%s","signature":"%s","took":250}"""
                .formatted(challenge.algorithm(), challenge.challenge(), number, challenge.salt(), signature);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("A correctly solved challenge should be accepted")
    void shouldAcceptCorrectSolution() {
        AltchaService.Challenge challenge = altchaService.createChallenge();

        assertThat(challenge.salt()).contains("?expires=");
        assertThat(challenge.maxnumber()).isEqualTo(AltchaService.MAX_NUMBER);
        assertThat(altchaService.verify(payload(challenge, solve(challenge), challenge.signature()))).isTrue();
    }

    @Test
    @DisplayName("The same solution must not be used twice (e.g. by a bot for many forms)")
    void shouldRejectReusedSolution() {
        AltchaService.Challenge challenge = altchaService.createChallenge();
        String payload = payload(challenge, solve(challenge), challenge.signature());

        assertThat(altchaService.verify(payload)).isTrue();
        assertThat(altchaService.verify(payload)).isFalse();
    }

    @Test
    @DisplayName("A wrong number should be rejected")
    void shouldRejectWrongNumber() {
        AltchaService.Challenge challenge = altchaService.createChallenge();
        long wrongNumber = (solve(challenge) + 1) % (AltchaService.MAX_NUMBER + 1);

        assertThat(altchaService.verify(payload(challenge, wrongNumber, challenge.signature()))).isFalse();
    }

    @Test
    @DisplayName("A challenge not issued by this server (wrong signature) should be rejected")
    void shouldRejectForgedChallenge() {
        // A bot creates its own easy challenge: number 0, so it does not have to compute anything
        String salt = "abc?expires=" + (System.currentTimeMillis() / 1000 + 600);
        AltchaService.Challenge forged = new AltchaService.Challenge(
                "SHA-256", AltchaService.sha256Hex(salt + 0), 0, salt, "00".repeat(32));

        assertThat(altchaService.verify(payload(forged, 0, forged.signature()))).isFalse();
    }

    @Test
    @DisplayName("A challenge issued by another instance (other HMAC key) should be rejected")
    void shouldRejectChallengeFromAnotherKey() {
        AltchaService.Challenge challenge = new AltchaService().createChallenge();

        assertThat(altchaService.verify(payload(challenge, solve(challenge), challenge.signature()))).isFalse();
    }

    @Test
    @DisplayName("A missing or malformed payload should be rejected without an exception")
    void shouldRejectMalformedPayload() {
        assertThat(altchaService.verify(null)).isFalse();
        assertThat(altchaService.verify("")).isFalse();
        assertThat(altchaService.verify("not base64!")).isFalse();
        assertThat(altchaService.verify(Base64.getEncoder().encodeToString("not json".getBytes()))).isFalse();
        assertThat(altchaService.verify(Base64.getEncoder().encodeToString("{}".getBytes()))).isFalse();
    }
}
