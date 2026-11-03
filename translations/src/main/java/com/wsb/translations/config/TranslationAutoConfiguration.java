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
 * Auto-configuration of the translations module. Spring Boot loads it automatically when the module is on the classpath
 * (registered in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}),
 * so the application does not have to configure anything manually.
 * <p>
 * It runs before Spring Boot's auto-configurations, so our {@code messageSource}
 * and {@code localeResolver} beans replace the default ones.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration"
})
@EnableConfigurationProperties(TranslationProperties.class)
public class TranslationAutoConfiguration {

    /**
     * Translation source. Bundles are discovered automatically - every {@code i18n/<bundle>/} directory
     * with a {@code messages.properties} file becomes a separate bundle. Adding a new bundle requires no code changes.
     */
    @Bean(name = "messageSource")
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource messageSource(TranslationProperties properties) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(findBundleBasenames(properties.getBundleLocationPattern()));
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // No file for the given language -> the base file (Polish), not the server operating system's language
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    @ConditionalOnMissingBean(TranslationService.class)
    public TranslationService translationService(MessageSource messageSource, TranslationProperties properties) {
        return new PropertiesTranslationService(messageSource, properties);
    }

    /**
     * Converts files found by the pattern (e.g. {@code .../i18n/ads/messages.properties})
     * into bundle base names for MessageSource (e.g. {@code classpath:i18n/ads/messages}).
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
     * Web configuration: choosing the language for a request and switching it with the {@code ?lang=} parameter.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebLocaleConfiguration implements WebMvcConfigurer {
        private final TranslationProperties properties;

        WebLocaleConfiguration(TranslationProperties properties) {
            this.properties = properties;
        }

        /**
         * Request language: first the cookie with the selected language, then the browser header
         * (Accept-Language), and if no supported language matches - the default language.
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
     * Interceptor that changes the language based on a URL parameter and accepts only supported languages
     * (e.g. {@code ?lang=de} when only pl/en are supported falls back to the default language).
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
