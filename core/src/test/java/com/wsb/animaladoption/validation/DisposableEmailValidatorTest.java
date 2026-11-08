package com.wsb.animaladoption.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisposableEmailValidatorTest {

    private final DisposableEmailValidator validator = new DisposableEmailValidator();

    @Test
    @DisplayName("Addresses from disposable mailbox services should be rejected (also subdomains, any letter case)")
    void shouldRejectDisposableDomains() {
        assertThat(validator.isValid("bot@mailinator.com", null)).isFalse();
        assertThat(validator.isValid("bot@YOPMAIL.com", null)).isFalse();
        assertThat(validator.isValid("bot@abc.guerrillamail.com", null)).isFalse();
    }

    @Test
    @DisplayName("Ordinary addresses should be accepted")
    void shouldAcceptRegularDomains() {
        assertThat(validator.isValid("jan@gmail.com", null)).isTrue();
        assertThat(validator.isValid("jan@wsb.pl", null)).isTrue();
        // Only the whole domain counts - "notmailinator.com" is a different domain
        assertThat(validator.isValid("jan@notmailinator.com", null)).isTrue();
    }

    @Test
    @DisplayName("An empty or malformed address is left to @NotBlank / @Email")
    void shouldIgnoreEmptyOrMalformed() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("no-at-sign", null)).isTrue();
    }
}
