package com.wsb.animaladoption.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task (every hour, at minute 0) that removes accounts never confirmed by email
 * (and, at minute 30, expired password reset links).
 * Thanks to it, accounts created en masse by bots (and accounts with a typo in the email)
 * do not stay in the database forever and do not block the email address for its real owner.
 *
 * Scheduling is enabled by @EnableScheduling in the web module (SchedulingConfig).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnverifiedAccountCleanupJob {
    private final UserService userService;

    @Scheduled(cron = "0 0 * * * *")
    public void deleteExpiredUnverifiedAccounts() {
        int deleted = userService.deleteExpiredUnverifiedAccounts();
        if (deleted > 0) {
            log.info("Deleted {} unconfirmed accounts with an expired activation link", deleted);
        }
    }

    /** Unused "set a new password" links - they expire after an hour, so they are cleaned up every hour as well. */
    @Scheduled(cron = "0 30 * * * *")
    public void deleteExpiredPasswordResetTokens() {
        int deleted = userService.deleteExpiredPasswordResetTokens();
        if (deleted > 0) {
            log.info("Deleted {} expired password reset links", deleted);
        }
    }
}
