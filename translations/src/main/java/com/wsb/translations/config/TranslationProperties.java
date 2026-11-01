package com.wsb.translations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * Ustawienia modułu tłumaczeń, które aplikacja może nadpisać w {@code application.yml}:
 * <pre>
 * app:
 *   i18n:
 *     default-locale: pl
 *     supported-locales: pl, en
 * </pre>
 */
@ConfigurationProperties(prefix = "app.i18n")
public class TranslationProperties {

    /** Język domyślny - używany, gdy przeglądarka nie wskazuje obsługiwanego języka. */
    private Locale defaultLocale = Locale.of("pl");

    /** Obsługiwane języki (kolejność = kolejność w przełączniku języka). */
    private List<Locale> supportedLocales = List.of(Locale.of("pl"), Locale.ENGLISH);

    /** Nazwa parametru URL zmieniającego język, np. {@code ?lang=en}. */
    private String paramName = "lang";

    /** Nazwa ciasteczka zapamiętującego wybrany język. */
    private String cookieName = "lang";

    /** Wzorzec wyszukiwania paczek tłumaczeń na classpath. */
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

    /** Sprawdza, czy dany język (porównywany po kodzie języka, np. "en") jest obsługiwany. */
    public boolean isSupported(Locale locale) {
        return locale != null && supportedLocales.stream()
                .anyMatch(supported -> supported.getLanguage().equals(locale.getLanguage()));
    }
}
