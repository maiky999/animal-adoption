package com.wsb.translations;

import com.wsb.translations.config.TranslationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the translation files consistent - it is easy to forget to add a key in one of the languages.
 */
class TranslationBundlesConsistencyTest {

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    @DisplayName("Each bundle should have the same keys in all supported languages")
    void everyBundleShouldHaveSameKeysInAllLanguages() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Resource base : findBaseBundles()) {
            Set<String> baseKeys = load(base).stringPropertyNames();
            for (Locale locale : new TranslationProperties().getSupportedLocales()) {
                Resource translated = base.createRelative("messages_" + locale.getLanguage() + ".properties");
                if (!translated.exists()) {
                    // the base file serves as the default language (Polish)
                    continue;
                }
                Set<String> translatedKeys = load(translated).stringPropertyNames();

                Set<String> missing = new TreeSet<>(baseKeys);
                missing.removeAll(translatedKeys);
                Set<String> extra = new TreeSet<>(translatedKeys);
                extra.removeAll(baseKeys);

                if (!missing.isEmpty()) problems.add(bundleName(base) + " [" + locale + "] brakuje: " + missing);
                if (!extra.isEmpty()) problems.add(bundleName(base) + " [" + locale + "] nadmiarowe: " + extra);
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("Each supported non-default language should have its own file in every bundle")
    void everyNonDefaultLanguageShouldHaveFile() throws IOException {
        TranslationProperties properties = new TranslationProperties();
        List<String> missingFiles = new ArrayList<>();

        for (Resource base : findBaseBundles()) {
            for (Locale locale : properties.getSupportedLocales()) {
                if (locale.equals(properties.getDefaultLocale())) continue;
                if (!base.createRelative("messages_" + locale.getLanguage() + ".properties").exists()) {
                    missingFiles.add(bundleName(base) + "/messages_" + locale.getLanguage() + ".properties");
                }
            }
        }

        assertThat(missingFiles).isEmpty();
    }

    @Test
    @DisplayName("The same key should not be defined in two different bundles")
    void keysShouldBeUniqueAcrossBundles() throws IOException {
        Map<String, String> keyToBundle = new HashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (Resource base : findBaseBundles()) {
            for (String key : load(base).stringPropertyNames()) {
                String previous = keyToBundle.putIfAbsent(key, bundleName(base));
                if (previous != null) duplicates.add(key + " (" + previous + ", " + bundleName(base) + ")");
            }
        }

        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("No translation should be empty")
    void translationsShouldNotBeBlank() throws IOException {
        List<String> blank = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath*:i18n/*/messages*.properties")) {
            Properties properties = load(resource);
            properties.stringPropertyNames().stream()
                    .filter(key -> properties.getProperty(key).isBlank())
                    .forEach(key -> blank.add(bundleName(resource) + ": " + key));
        }
        assertThat(blank).isEmpty();
    }

    private Resource[] findBaseBundles() throws IOException {
        Resource[] resources = resolver.getResources(new TranslationProperties().getBundleLocationPattern());
        assertThat(resources).as("nie znaleziono żadnej paczki tłumaczeń").isNotEmpty();
        return resources;
    }

    private static Properties load(Resource resource) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String bundleName(Resource resource) {
        try {
            String url = resource.getURL().toString();
            return url.substring(url.lastIndexOf("i18n/"));
        } catch (IOException e) {
            return String.valueOf(resource);
        }
    }
}
