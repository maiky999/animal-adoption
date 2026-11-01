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
    @DisplayName("Powinien zwrócić tłumaczenie w języku bieżącego żądania")
    void shouldTranslateUsingCurrentLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(translationService.get("nav.ads")).isEqualTo("Listings");

        LocaleContextHolder.setLocale(PL);
        assertThat(translationService.get("nav.ads")).isEqualTo("Ogłoszenia");
    }

    @Test
    @DisplayName("Powinien zwrócić tłumaczenie we wskazanym języku niezależnie od żądania")
    void shouldTranslateUsingGivenLocale() {
        LocaleContextHolder.setLocale(PL);
        assertThat(translationService.get(Locale.ENGLISH, "nav.login")).isEqualTo("Log in");
    }

    @Test
    @DisplayName("Powinien wstawić argumenty w miejsca {0}, {1}...")
    void shouldFormatArguments() {
        String subject = translationService.get(Locale.ENGLISH, "email.adoption.subject", "Burek");
        assertThat(subject).isEqualTo("New pre-adoption survey: Burek");
    }

    @Test
    @DisplayName("Dla nieobsługiwanego języka powinien użyć języka domyślnego (polskiego), a nie języka systemu")
    void shouldFallbackToDefaultLanguage() {
        assertThat(translationService.get(Locale.GERMAN, "nav.ads")).isEqualTo("Ogłoszenia");
    }

    @Test
    @DisplayName("Powinien zwrócić wartość domyślną dla nieistniejącego klucza")
    void shouldReturnDefaultValueForMissingKey() {
        assertThat(translationService.getOrDefault("category.nowa_kategoria", "Nowa kategoria"))
                .isEqualTo("Nowa kategoria");
    }

    @Test
    @DisplayName("Powinien rzucić wyjątek dla nieistniejącego klucza")
    void shouldThrowForMissingKey() {
        assertThatThrownBy(() -> translationService.get("nie.ma.takiego.klucza"))
                .isInstanceOf(NoSuchMessageException.class);
    }

    @Test
    @DisplayName("Powinien przetłumaczyć obiekt niosący klucz (np. wyjątek biznesowy)")
    void shouldTranslateResolvable() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(translationService.get(new DefaultMessageSourceResolvable("profile.error.wrongPassword")))
                .isEqualTo("The current password is incorrect.");
    }

    @Test
    @DisplayName("Powinien udostępnić listę obsługiwanych języków i język domyślny")
    void shouldExposeSupportedLocales() {
        assertThat(translationService.getSupportedLocales()).containsExactly(PL, Locale.ENGLISH);
        assertThat(translationService.getDefaultLocale()).isEqualTo(PL);
    }
}
