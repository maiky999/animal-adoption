package com.wsb.translations.config;

import com.wsb.translations.TranslationService;
import com.wsb.translations.impl.PropertiesTranslationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

/**
 * Autokonfiguracja modułu tłumaczeń. Spring Boot ładuje ją automatycznie, gdy moduł jest na classpath
 * (rejestracja w {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}),
 * więc aplikacja nie musi niczego konfigurować ręcznie.
 * <p>
 * Uruchamiana jest przed autokonfiguracjami Spring Boota, dzięki czemu nasze beany {@code messageSource}
 * i {@code localeResolver} zastępują domyślne.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration"
})
@EnableConfigurationProperties(TranslationProperties.class)
public class TranslationAutoConfiguration {

    /**
     * Źródło tłumaczeń. Paczki są wyszukiwane automatycznie - każdy katalog {@code i18n/<paczka>/}
     * z plikiem {@code messages.properties} staje się osobną paczką. Dodanie nowej paczki nie wymaga zmian w kodzie.
     */
    @Bean(name = "messageSource")
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource messageSource(TranslationProperties properties) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(findBundleBasenames(properties.getBundleLocationPattern()));
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Brak pliku dla danego języka -> plik bazowy (polski), a nie język systemu operacyjnego serwera
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    @ConditionalOnMissingBean(TranslationService.class)
    public TranslationService translationService(MessageSource messageSource, TranslationProperties properties) {
        return new PropertiesTranslationService(messageSource, properties);
    }

    /**
     * Zamienia pliki znalezione wg wzorca (np. {@code .../i18n/ads/messages.properties})
     * na nazwy bazowe paczek dla MessageSource (np. {@code classpath:i18n/ads/messages}).
     */
    static String[] findBundleBasenames(String locationPattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            return Arrays.stream(resources)
                    .map(TranslationAutoConfiguration::toBasename)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toArray(String[]::new);
        } catch (IOException e) {
            throw new UncheckedIOException("Nie udało się wyszukać paczek tłumaczeń: " + locationPattern, e);
        }
    }

    private static String toBasename(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int index = url.lastIndexOf("i18n/");
            if (index < 0) {
                return null;
            }
            return "classpath:" + url.substring(index).replace(".properties", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Konfiguracja webowa: wybór języka dla żądania i jego przełączanie parametrem {@code ?lang=}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebLocaleConfiguration implements WebMvcConfigurer {
        private final TranslationProperties properties;

        WebLocaleConfiguration(TranslationProperties properties) {
            this.properties = properties;
        }

        /**
         * Język żądania: najpierw ciasteczko z wybranym językiem, potem nagłówek przeglądarki
         * (Accept-Language), a gdy żaden obsługiwany język nie pasuje - język domyślny.
         */
        @Bean(name = "localeResolver")
        @ConditionalOnMissingBean(name = "localeResolver")
        public LocaleResolver localeResolver() {
            CookieLocaleResolver resolver = new CookieLocaleResolver(properties.getCookieName());
            resolver.setCookieMaxAge(Duration.ofDays(365));
            resolver.setDefaultLocaleFunction(this::resolveFromBrowser);
            return resolver;
        }

        private Locale resolveFromBrowser(HttpServletRequest request) {
            if (request.getHeader("Accept-Language") == null) {
                return properties.getDefaultLocale();
            }
            return Collections.list(request.getLocales()).stream()
                    .filter(properties::isSupported)
                    .map(locale -> Locale.of(locale.getLanguage()))
                    .findFirst()
                    .orElse(properties.getDefaultLocale());
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new SupportedLocaleChangeInterceptor(properties));
        }
    }

    /**
     * Interceptor zmieniający język po parametrze URL, który akceptuje wyłącznie obsługiwane języki
     * (np. {@code ?lang=de} przy obsłudze tylko pl/en przywraca język domyślny).
     */
    static class SupportedLocaleChangeInterceptor extends LocaleChangeInterceptor {
        private final TranslationProperties properties;

        SupportedLocaleChangeInterceptor(TranslationProperties properties) {
            this.properties = properties;
            setParamName(properties.getParamName());
            setIgnoreInvalidLocale(true);
        }

        @Override
        protected Locale parseLocaleValue(String localeValue) {
            Locale locale = super.parseLocaleValue(localeValue);
            return properties.isSupported(locale) ? Locale.of(locale.getLanguage()) : properties.getDefaultLocale();
        }
    }
}
