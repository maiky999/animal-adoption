package com.wsb.translations.impl;

import com.wsb.translations.TranslationService;
import com.wsb.translations.config.TranslationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

/**
 * {@link TranslationService} implementation based on Spring {@link MessageSource},
 * which reads translations from {@code i18n/<bundle>/messages[_language].properties} files.
 * <p>
 * The language of the current request is taken from {@link LocaleContextHolder}, which Spring MVC
 * sets based on the {@link org.springframework.web.servlet.LocaleResolver} (cookie / browser header).
 */
public class PropertiesTranslationService implements TranslationService {
    private final MessageSource messageSource;
    private final TranslationProperties properties;

    public PropertiesTranslationService(MessageSource messageSource, TranslationProperties properties) {
        this.messageSource = messageSource;
        this.properties = properties;
    }

    @Override
    public String get(String key, Object... args) {
        return get(getCurrentLocale(), key, args);
    }

    @Override
    public String get(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    @Override
    public String get(MessageSourceResolvable resolvable) {
        return messageSource.getMessage(resolvable, getCurrentLocale());
    }

    @Override
    public String getOrDefault(String key, String defaultValue, Object... args) {
        return messageSource.getMessage(key, args, defaultValue, getCurrentLocale());
    }

    @Override
    public Locale getCurrentLocale() {
        return LocaleContextHolder.getLocale();
    }

    @Override
    public Locale getDefaultLocale() {
        return properties.getDefaultLocale();
    }

    @Override
    public List<Locale> getSupportedLocales() {
        return properties.getSupportedLocales();
    }
}
