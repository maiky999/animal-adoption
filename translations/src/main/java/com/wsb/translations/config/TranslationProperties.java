package com.wsb.translations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * Settings of the translations module that the application can override in {@code application.yml}:
 * <pre>
 * app:
 *   i18n:
 *     default-locale: pl
 *     supported-locales: pl, en
 * </pre>
 */
@ConfigurationProperties(prefix = "app.i18n")
public class TranslationProperties {

    /** Default language - used when the browser does not indicate a supported language. */
    private Locale defaultLocale = Locale.of("pl");

    /** Supported languages (order = order in the language switcher). */
    private List<Locale> supportedLocales = List.of(Locale.of("pl"), Locale.ENGLISH);

    /** Name of the URL parameter that changes the language, e.g. {@code ?lang=en}. */
    private String paramName = "lang";

    /** Name of the cookie that remembers the selected language. */
    private String cookieName = "lang";

    /** Pattern for finding translation bundles on the classpath. */
    private String bundleLocationPattern = "classpath*:i18n/*/messages.properties";

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public List<Locale> getSupportedLocales() {
        return supportedLocales;
    }

    public void setSupportedLocales(List<Locale> supportedLocales) {
        this.supportedLocales = supportedLocales;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getBundleLocationPattern() {
        return bundleLocationPattern;
    }

    public void setBundleLocationPattern(String bundleLocationPattern) {
        this.bundleLocationPattern = bundleLocationPattern;
    }

    /** Checks whether the given language (compared by language code, e.g. "en") is supported. */
    public boolean isSupported(Locale locale) {
        return locale != null && supportedLocales.stream()
                .anyMatch(supported -> supported.getLanguage().equals(locale.getLanguage()));
    }
}
