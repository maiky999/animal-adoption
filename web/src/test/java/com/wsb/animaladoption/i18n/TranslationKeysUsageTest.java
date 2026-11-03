package com.wsb.animaladoption.i18n;

import com.wsb.translations.config.TranslationAutoConfiguration;
import com.wsb.translations.config.TranslationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprawdza, czy każdy klucz tłumaczenia użyty w aplikacji (szablony Thymeleaf, adnotacje walidacji,
 * komunikaty w kontrolerach i wyjątkach) istnieje w module tłumaczeń - w każdym obsługiwanym języku.
 * Literówka w kluczu zostanie wykryta przez test, a nie dopiero przez użytkownika ("??klucz_pl??").
 */
class TranslationKeysUsageTest {

    /** #{klucz} w szablonach (pomija dynamiczne #{${...}}). */
    private static final Pattern TEMPLATE_KEY = Pattern.compile("#\\{([a-zA-Z][\\w.]*)}");
    /** #messages.msg('klucz') w wyrażeniach Thymeleaf. */
    private static final Pattern TEMPLATE_MSG_KEY = Pattern.compile("#messages\\.msg\\('([\\w.]+)'\\)");
    /** "{klucz}" w adnotacjach walidacji. */
    private static final Pattern VALIDATION_KEY = Pattern.compile("\"\\{([a-zA-Z][\\w.]*)}\"");
    /** Klucze przekazywane jako komunikaty flash, kody błędów i klucze wyjątków. */
    private static final Pattern JAVA_KEY = Pattern.compile(
            "\"((?:ads|adoption|verify|profile|articles|validation|email)\\.[\\w.]+)\"");

    private final MessageSource messageSource =
            new TranslationAutoConfiguration().messageSource(new TranslationProperties());

    @Test
    @DisplayName("Wszystkie klucze użyte w szablonach i kodzie powinny istnieć w każdym języku")
    void allUsedKeysShouldExist() throws IOException {
        Set<String> keys = new TreeSet<>();
        keys.addAll(findKeys(Path.of("src/main/resources/templates"), ".html", TEMPLATE_KEY, TEMPLATE_MSG_KEY));
        // Test uruchamia się w katalogu modułu web/ - kod Javy z kluczami leży też w sąsiednich modułach
        // core/ (DTO z walidacją, serwisy) i domain/ (wyjątki), więc skanujemy wszystkie trzy.
        for (String module : List.of(".", "../core", "../domain")) {
            keys.addAll(findKeys(Path.of(module, "src/main/java"), ".java", VALIDATION_KEY, JAVA_KEY));
        }

        assertThat(keys).as("nie znaleziono żadnych kluczy - zła ścieżka?").hasSizeGreaterThan(100);

        List<String> missing = new ArrayList<>();
        for (Locale locale : new TranslationProperties().getSupportedLocales()) {
            for (String key : keys) {
                try {
                    messageSource.getMessage(key, null, locale);
                } catch (NoSuchMessageException e) {
                    missing.add(locale + ": " + key);
                }
            }
        }

        assertThat(missing).isEmpty();
    }

    private static Set<String> findKeys(Path root, String extension, Pattern... patterns) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(extension)).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(content);
                    while (matcher.find()) {
                        keys.add(matcher.group(1));
                    }
                }
            }
        }
        return keys;
    }
}
