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
    @DisplayName("Should choose the default language without a cookie and browser header")
    void shouldUseDefaultLocaleWithoutHints() {
        assertThat(localeResolver.resolveLocale(new MockHttpServletRequest())).isEqualTo(PL);
    }

    @Test
    @DisplayName("Should choose the first supported language from the Accept-Language header")
    void shouldUseSupportedBrowserLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE,en-US;q=0.8,pl;q=0.5");

        assertThat(localeResolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Should choose the default language for an unsupported browser language")
    void shouldFallbackForUnsupportedBrowserLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE");

        assertThat(localeResolver.resolveLocale(request)).isEqualTo(PL);
    }

    @Test
    @DisplayName("The ?lang= parameter should change the language and remember it in a cookie")
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
    @DisplayName("An unsupported language from the parameter should be replaced with the default language")
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
