package com.wsb.animaladoption.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Filter that limits how often sensitive endpoints can be called (protection against bots and mass automation,
 * e.g. registering thousands of accounts, guessing passwords or flooding other users with messages).
 *
 * Each rule describes: which requests it covers, how many of them are allowed and in what time window.
 * Anonymous requests are counted per IP address, logged-in users per account
 * (so many users behind one shared IP, e.g. a university network, do not block each other).
 *
 * When the limit is exceeded, the client gets HTTP 429 Too Many Requests with a Retry-After header,
 * and the request never reaches the controller (no database query, no BCrypt hashing, no email is sent).
 *
 * The filter is added to the Spring Security chain in SecurityConfig - before the login filter,
 * so it also covers POST /login.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * One limit rule.
     *
     * @param name    rule name - part of the counter key, so different rules have separate counters
     * @param method  HTTP method the rule applies to
     * @param pattern URL pattern (Ant style, e.g. "/messages/**")
     * @param limit   number of requests allowed in one window
     * @param window  length of the window
     */
    record Rule(String name, String method, String pattern, int limit, Duration window) {
    }

    /** Limits for the endpoints most exposed to abuse. */
    static final List<Rule> RULES = List.of(
            // Registration: hashing the password + sending an email - the most expensive and most abused form
            new Rule("register", "POST", "/register", 5, Duration.ofHours(1)),
            // Login: guessing passwords (independently, LoginAttemptService locks a single account)
            new Rule("login", "POST", "/login", 10, Duration.ofMinutes(1)),
            // "Forgot password?": each request may send an email; the token is 256-bit, so guessing it is hopeless anyway
            new Rule("forgot-password", "POST", "/forgot-password", 5, Duration.ofHours(1)),
            new Rule("reset-password", "POST", "/reset-password", 10, Duration.ofHours(1)),
            // New ALTCHA challenges - every page view fetches one, so the limit is loose
            new Rule("altcha", "GET", "/altcha/challenge", 30, Duration.ofMinutes(1)),
            // Chat messages
            new Rule("messages", "POST", "/messages/**", 20, Duration.ofMinutes(1)),
            // New listings and adoption forms (each form sends an email to the listing owner)
            new Rule("new-ad", "POST", "/ads/new", 10, Duration.ofHours(1)),
            new Rule("adoption-form", "POST", "/ads/*/adoption-form", 10, Duration.ofHours(1)),
            // Profile changes - an email change sends a confirmation link
            new Rule("profile", "POST", "/profile/**", 10, Duration.ofMinutes(10)),
            // Revealing phone numbers - a person checks a few listings, a bot collecting numbers needs thousands
            new Rule("phone", "GET", "/users/*/phone", 30, Duration.ofHours(1))
    );

    private final RateLimiter rateLimiter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = findRule(request);
        if (rule != null) {
            String key = rule.name() + ":" + clientKey(request);
            if (!rateLimiter.tryAcquire(key, rule.limit(), rule.window())) {
                log.warn("Rate limit exceeded: rule={}, client={}", rule.name(), clientKey(request));
                response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));
                // sendError passes the request to Spring Boot's error handling, which renders templates/error/429.html
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Rule findRule(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Rule rule : RULES) {
            if (rule.method().equalsIgnoreCase(request.getMethod()) && pathMatcher.match(rule.pattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Who is making the request: the account for a logged-in user, otherwise the IP address.
     * Behind a reverse proxy (nginx, Cloudflare) getRemoteAddr() returns the real client IP only when
     * server.forward-headers-strategy is enabled - see application.yml.
     */
    private String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "user:" + authentication.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
