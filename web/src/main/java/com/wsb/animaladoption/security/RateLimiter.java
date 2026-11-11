package com.wsb.animaladoption.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory request limiter using the "fixed window" algorithm.
 *
 * For every key (e.g. "register:203.0.113.7") we remember when the current time window started
 * and how many requests were made in it. When the window ends, the counter starts from zero again.
 * Example: limit 5 per hour - the 6th registration from the same IP within that hour is rejected.
 *
 * Known trade-off of a fixed window: a client can make up to 2x the limit around the boundary of two windows
 * (end of one + start of the next). For protecting forms against mass automation this is acceptable,
 * and the algorithm is much easier to understand than a sliding window or a token bucket.
 *
 * The data lives in the memory of a single application instance - with several instances behind a load balancer,
 * a shared store (e.g. Redis) would be needed instead of the map.
 */
@Component
public class RateLimiter {

    /** Counter for one key: when the current window ends and how many requests it already contains. */
    private record Window(Instant endsAt, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Registers a request and checks whether it fits within the limit.
     *
     * @param key    who is making the request and to which rule it belongs (e.g. "login:203.0.113.7")
     * @param limit  maximum number of requests in one window
     * @param window length of the window
     * @return true if the request is allowed, false if the limit has been exceeded
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        // compute() is atomic for a single key, so two parallel requests cannot both "see" the same counter value
        Window updated = windows.compute(key, (k, current) -> {
            if (current == null || !now.isBefore(current.endsAt())) {
                return new Window(now.plus(window), 1);
            }
            return new Window(current.endsAt(), current.count() + 1);
        });
        return updated.count() <= limit;
    }

    /** Removes finished windows, so the map does not grow forever (e.g. with every new IP address). */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void removeExpiredWindows() {
        Instant now = Instant.now();
        windows.values().removeIf(window -> !now.isBefore(window.endsAt()));
    }
}
