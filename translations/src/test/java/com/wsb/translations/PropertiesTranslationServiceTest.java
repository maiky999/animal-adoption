package com.wsb.translations;

import com.wsb.translations.config.TranslationAutoConfiguration;
import com.wsb.translations.config.TranslationProperties;
import com.wsb.translations.impl.PropertiesTranslationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesTranslationServiceTest {

    private static final Locale PL = Locale.of("pl");

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        TranslationProperties properties = new TranslationProperties();
        MessageSource messageSource = new TranslationAutoConfiguration().messageSource(properties);
        translationService = new PropertiesTranslationService(messageSource, properties);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Should return the translation in the language of the current request")
    void shouldTranslateUsingCurrentLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(translationService.get("nav.ads")).isEqualTo("Listings");

        LocaleContextHolder.setLocale(PL);
        assertThat(translationService.get("nav.ads")).isEqualTo("Ogłoszenia");
    }

    @Test
    @DisplayName("Should return the translation in the given language regardless of the request")
    void shouldTranslateUsingGivenLocale() {
        LocaleContextHolder.setLocale(PL);
        assertThat(translationService.get(Locale.ENGLISH, "nav.login")).isEqualTo("Log in");
    }

    @Test
    @DisplayName("Should insert arguments in place of {0}, {1}...")
    void shouldFormatArguments() {
        String subject = translationService.get(Locale.ENGLISH, "email.adoption.subject", "Burek");
        assertThat(subject).isEqualTo("New pre-adoption survey: Burek");
    }

    @Test
    @DisplayName("Should use the default language (Polish), not the system language, for an unsupported language")
    void shouldFallbackToDefaultLanguage() {
        assertThat(translationService.get(Locale.GERMAN, "nav.ads")).isEqualTo("Ogłoszenia");
    }

    @Test
    @DisplayName("Should return the default value for a non-existent key")
    void shouldReturnDefaultValueForMissingKey() {
        assertThat(translationService.getOrDefault("category.nowa_kategoria", "Nowa kategoria"))
                .isEqualTo("Nowa kategoria");
    }

    @Test
    @DisplayName("Should throw an exception for a non-existent key")
    void shouldThrowForMissingKey() {
        assertThatThrownBy(() -> translationService.get("nie.ma.takiego.klucza"))
                .isInstanceOf(NoSuchMessageException.class);
    }

    @Test
    @DisplayName("Should translate an object carrying a key (e.g. a business exception)")
    void shouldTranslateResolvable() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(translationService.get(new DefaultMessageSourceResolvable("profile.error.wrongPassword")))
                .isEqualTo("The current password is incorrect.");
    }

    @Test
    @DisplayName("Should expose the list of supported languages and the default language")
    void shouldExposeSupportedLocales() {
        assertThat(translationService.getSupportedLocales()).containsExactly(PL, Locale.ENGLISH);
        assertThat(translationService.getDefaultLocale()).isEqualTo(PL);
    }
}
