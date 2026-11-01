package com.wsb.translations.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class WebLocaleConfigurationTest {

    private static final Locale PL = Locale.of("pl");

    private final TranslationProperties properties = new TranslationProperties();
    private final LocaleResolver localeResolver =
            new TranslationAutoConfiguration.WebLocaleConfiguration(properties).localeResolver();

    @Test
    @DisplayName("Bez ciasteczka i nagłówka przeglądarki powinien wybrać język domyślny")
    void shouldUseDefaultLocaleWithoutHints() {
        assertThat(localeResolver.resolveLocale(new MockHttpServletRequest())).isEqualTo(PL);
    }

    @Test
    @DisplayName("Powinien wybrać pierwszy obsługiwany język z nagłówka Accept-Language")
    void shouldUseSupportedBrowserLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE,en-US;q=0.8,pl;q=0.5");

        assertThat(localeResolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Dla nieobsługiwanego języka przeglądarki powinien wybrać język domyślny")
    void shouldFallbackForUnsupportedBrowserLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE");

        assertThat(localeResolver.resolveLocale(request)).isEqualTo(PL);
    }

    @Test
    @DisplayName("Parametr ?lang= powinien zmienić język i zapamiętać go w ciasteczku")
    void shouldChangeLocaleWithParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("lang", "en");
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, localeResolver);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TranslationAutoConfiguration.SupportedLocaleChangeInterceptor(properties)
                .preHandle(request, response, new Object());

        assertThat(response.getCookie(properties.getCookieName())).isNotNull();
        assertThat(response.getCookie(properties.getCookieName()).getValue()).isEqualTo("en");
    }

    @Test
    @DisplayName("Nieobsługiwany język z parametru powinien zostać zastąpiony językiem domyślnym")
    void shouldRejectUnsupportedLanguageParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("lang", "de");
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, localeResolver);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TranslationAutoConfiguration.SupportedLocaleChangeInterceptor(properties)
                .preHandle(request, response, new Object());

        assertThat(response.getCookie(properties.getCookieName()).getValue()).isEqualTo("pl");
    }
}
