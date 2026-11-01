package com.wsb.animaladoption.exception;

import org.springframework.context.MessageSourceResolvable;

/**
 * Wyjątek biznesowy, którego komunikat jest pokazywany użytkownikowi.
 * <p>
 * Zamiast gotowego zdania przechowuje klucz tłumaczenia (np. {@code profile.error.wrongPassword}).
 * Dzięki temu warstwa serwisów nie zależy od języka użytkownika - tekst jest tłumaczony dopiero w widoku
 * lub przez {@link com.wsb.translations.TranslationService#get(MessageSourceResolvable)}.
 * <p>
 * Dziedziczy po {@link IllegalArgumentException}, więc zachowuje zgodność z dotychczasową obsługą błędów.
 */
public class LocalizedException extends IllegalArgumentException implements MessageSourceResolvable {
    private final String messageKey;
    private final transient Object[] arguments;

    public LocalizedException(String messageKey, Object... arguments) {
        super(messageKey);
        this.messageKey = messageKey;
        this.arguments = arguments;
    }

    public String getMessageKey() {
        return messageKey;
    }

    @Override
    public String[] getCodes() {
        return new String[]{messageKey};
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }
}
