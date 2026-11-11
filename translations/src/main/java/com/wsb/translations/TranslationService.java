package com.wsb.translations;

import org.springframework.context.MessageSourceResolvable;

import java.util.List;
import java.util.Locale;

/**
 * Translation service - the single access point to application texts in Java code.
 * <p>
 * Texts are stored as key-value pairs (e.g. {@code nav.ads=Ogłoszenia}). Application code
 * uses only the keys, and the service returns the value in the appropriate language.
 * Thymeleaf templates use the same translations directly, through {@code #{key}} expressions.
 * <p>
 * The interface separates the application from the translation source. The current implementation reads {@code .properties} files,
 * but it can be replaced, e.g. with a database-backed implementation, without changes in the other layers.
 */
public interface TranslationService {

    /**
     * Returns the translation of the key in the language of the current request (or the default one when there is no request).
     *
     * @param key  translation key, e.g. {@code "ad.created.pending"}
     * @param args optional arguments inserted in place of {@code {0}}, {@code {1}}...
     * @throws org.springframework.context.NoSuchMessageException if the key does not exist
     */
    String get(String key, Object... args);

    /**
     * Returns the translation of the key in the given language - useful outside of an HTTP request,
     * e.g. when sending emails from the queue.
     */
    String get(Locale locale, String key, Object... args);

    /**
     * Returns the translation of an object that carries its own key and arguments (e.g. a business exception).
     */
    String get(MessageSourceResolvable resolvable);

    /**
     * Returns the translation of the key, or the given default value if the key does not exist.
     * Useful for data that does not always have a translation (e.g. categories added in the database).
     */
    String getOrDefault(String key, String defaultValue, Object... args);

    /** Returns the language of the current request. */
    Locale getCurrentLocale();

    /** Returns the application's default language. */
    Locale getDefaultLocale();

    /** Returns the list of languages supported by the application. */
    List<Locale> getSupportedLocales();
}
