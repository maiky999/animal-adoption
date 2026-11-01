package com.wsb.translations.impl;

import com.wsb.translations.TranslationService;
import com.wsb.translations.config.TranslationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

/**
 * Implementacja {@link TranslationService} oparta o Spring {@link MessageSource},
 * który czyta tłumaczenia z plików {@code i18n/<paczka>/messages[_jezyk].properties}.
 * <p>
 * Język bieżącego żądania pobierany jest z {@link LocaleContextHolder}, który Spring MVC
 * ustawia na podstawie {@link org.springframework.web.servlet.LocaleResolver} (ciasteczko / nagłówek przeglądarki).
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
