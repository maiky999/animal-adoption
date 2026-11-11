package com.wsb.animaladoption.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled methods: removing unconfirmed accounts (UnverifiedAccountCleanupJob)
 * and clearing expired in-memory counters (RateLimiter, LoginAttemptService, AltchaService).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
