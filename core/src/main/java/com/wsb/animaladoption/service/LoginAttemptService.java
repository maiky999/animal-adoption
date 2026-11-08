package com.wsb.animaladoption.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection against guessing passwords: after MAX_FAILURES wrong passwords for the same account
 * within LOCK_DURATION, logging in to that account is blocked for LOCK_DURATION.
 *
 * Failures are counted per account (email), not per IP - so an attacker using many IP addresses
 * still gets only a few attempts per account. The trade-off: someone could deliberately lock another user's
 * account for a moment, which is why the lock is short and expires on its own (no manual unlocking needed).
 * Mass attempts from a single IP are additionally limited by RateLimitFilter.
 *
 * Failed attempts are reported by LoginAttemptListener (Spring Security events), and the lock is enforced
 * by CustomUserDetailsService (it marks the account as locked, so Spring Security rejects the login).
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /**
     * @param failures    failed attempts in the current period
     * @param resetsAt    when the counter (and a possible lock) expires
     */
    private record Attempts(int failures, Instant resetsAt) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void loginFailed(String email) {
        Instant now = Instant.now();
        attempts.compute(normalize(email), (key, current) -> {
            if (current == null || !now.isBefore(current.resetsAt())) {
                return new Attempts(1, now.plus(LOCK_DURATION));
            }
            int failures = current.failures() + 1;
            // Reaching the limit starts the lock period from this moment
            Instant resetsAt = failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : current.resetsAt();
            return new Attempts(failures, resetsAt);
        });
    }

    public void loginSucceeded(String email) {
        attempts.remove(normalize(email));
    }

    public boolean isBlocked(String email) {
        Attempts current = attempts.get(normalize(email));
        return current != null
                && current.failures() >= MAX_FAILURES
                && Instant.now().isBefore(current.resetsAt());
    }

    /** Removes expired counters, so that the map does not keep every email ever typed in the login form. */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void removeExpiredEntries() {
        Instant now = Instant.now();
        attempts.values().removeIf(entry -> !now.isBefore(entry.resetsAt()));
    }

    /** Emails are stored in lowercase, so "Jan@X.pl" and "jan@x.pl" are the same account. */
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
