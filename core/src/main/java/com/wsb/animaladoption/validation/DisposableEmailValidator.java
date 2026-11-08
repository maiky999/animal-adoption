package com.wsb.animaladoption.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks the domain of an email address against the list of disposable mailbox services.
 * The list is loaded once from the classpath (it does not change while the application runs).
 */
public class DisposableEmailValidator implements ConstraintValidator<NotDisposableEmail, String> {

    private static final Set<String> DISPOSABLE_DOMAINS = loadDomains();

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        // An empty or malformed address is handled by @NotBlank / @Email - here we only look at the domain
        if (email == null || !email.contains("@")) {
            return true;
        }
        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
        return !isDisposable(domain);
    }

    /** Also matches subdomains, e.g. "abc.mailinator.com" when "mailinator.com" is on the list. */
    static boolean isDisposable(String domain) {
        String candidate = domain;
        while (true) {
            if (DISPOSABLE_DOMAINS.contains(candidate)) {
                return true;
            }
            int dot = candidate.indexOf('.');
            if (dot < 0) {
                return false;
            }
            candidate = candidate.substring(dot + 1);
        }
    }

    private static Set<String> loadDomains() {
        try (InputStream input = DisposableEmailValidator.class.getResourceAsStream("/disposable-email-domains.txt")) {
            if (input == null) {
                throw new IllegalStateException("Missing disposable-email-domains.txt on the classpath");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
