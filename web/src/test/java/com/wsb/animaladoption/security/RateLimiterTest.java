package com.wsb.animaladoption.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    @DisplayName("Should allow requests up to the limit and reject the next one")
    void shouldRejectRequestsAboveLimit() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire("register:1.2.3.4", 3, Duration.ofHours(1))).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("register:1.2.3.4", 3, Duration.ofHours(1))).isFalse();
    }

    @Test
    @DisplayName("Each key (client / rule) should have its own counter")
    void shouldCountKeysSeparately() {
        assertThat(rateLimiter.tryAcquire("register:1.2.3.4", 1, Duration.ofHours(1))).isTrue();
        assertThat(rateLimiter.tryAcquire("register:1.2.3.4", 1, Duration.ofHours(1))).isFalse();

        assertThat(rateLimiter.tryAcquire("register:5.6.7.8", 1, Duration.ofHours(1))).isTrue();
        assertThat(rateLimiter.tryAcquire("login:1.2.3.4", 1, Duration.ofHours(1))).isTrue();
    }

    @Test
    @DisplayName("After the window ends, the counter should start from zero again")
    void shouldResetCounterAfterWindowEnds() throws InterruptedException {
        assertThat(rateLimiter.tryAcquire("key", 1, Duration.ofMillis(20))).isTrue();
        assertThat(rateLimiter.tryAcquire("key", 1, Duration.ofMillis(20))).isFalse();

        Thread.sleep(40);

        assertThat(rateLimiter.tryAcquire("key", 1, Duration.ofMillis(20))).isTrue();
    }
}
