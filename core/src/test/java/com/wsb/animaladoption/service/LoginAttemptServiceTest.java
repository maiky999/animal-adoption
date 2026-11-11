package com.wsb.animaladoption.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService();

    private void failTimes(String email, int times) {
        for (int i = 0; i < times; i++) {
            service.loginFailed(email);
        }
    }

    @Test
    @DisplayName("The account should be locked only after MAX_FAILURES wrong passwords")
    void shouldBlockAfterMaxFailures() {
        failTimes("jan@example.com", LoginAttemptService.MAX_FAILURES - 1);
        assertThat(service.isBlocked("jan@example.com")).isFalse();

        service.loginFailed("jan@example.com");
        assertThat(service.isBlocked("jan@example.com")).isTrue();
    }

    @Test
    @DisplayName("A successful login should reset the counter")
    void shouldResetAfterSuccess() {
        failTimes("jan@example.com", LoginAttemptService.MAX_FAILURES - 1);
        service.loginSucceeded("jan@example.com");
        service.loginFailed("jan@example.com");

        assertThat(service.isBlocked("jan@example.com")).isFalse();
    }

    @Test
    @DisplayName("Letter case and spaces in the email should not matter")
    void shouldNormalizeEmail() {
        failTimes(" Jan@Example.COM ", LoginAttemptService.MAX_FAILURES);

        assertThat(service.isBlocked("jan@example.com")).isTrue();
    }

    @Test
    @DisplayName("Failures on one account should not lock another")
    void shouldCountAccountsSeparately() {
        failTimes("jan@example.com", LoginAttemptService.MAX_FAILURES);

        assertThat(service.isBlocked("anna@example.com")).isFalse();
    }
}
