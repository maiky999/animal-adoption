package com.wsb.animaladoption.exception;

import org.springframework.context.MessageSourceResolvable;

/**
 * Business exception whose message is shown to the user.
 * <p>
 * Instead of a ready-made sentence it stores a translation key (e.g. {@code profile.error.wrongPassword}).
 * This way the service layer does not depend on the user's language - the text is translated only in the view
 * or by {@link com.wsb.translations.TranslationService#get(MessageSourceResolvable)}.
 * <p>
 * Extends {@link IllegalArgumentException}, so it stays compatible with the existing error handling.
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
