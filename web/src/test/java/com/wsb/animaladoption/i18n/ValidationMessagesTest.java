package com.wsb.animaladoption.i18n;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: validation messages from DTOs ({@code message = "{key}"}) should be translated
 * by the translations module into the language of the current request, with {min}/{max} values filled in.
 */
@SpringBootTest
class ValidationMessagesTest {

    @Autowired
    private Validator validator;

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Validation messages should be in Polish for the Polish language")
    void shouldTranslateValidationMessagesToPolish() {
        LocaleContextHolder.setLocale(Locale.of("pl"));

        assertThat(messagesFor(new UserRegistrationDto())).contains("Adres email jest wymagany", "Hasło jest wymagane");
    }

    @Test
    @DisplayName("Validation messages should be in English for the English language")
    void shouldTranslateValidationMessagesToEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(messagesFor(new UserRegistrationDto())).contains("Email address is required", "Password is required");
    }

    @Test
    @DisplayName("Should insert the {min} and {max} values from the @Size annotation")
    void shouldInterpolateConstraintAttributes() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        AdCreateDto dto = new AdCreateDto();
        dto.setTitle("Krótki");

        assertThat(messagesFor(dto)).contains("Title must be between 10 and 150 characters long");
    }

    private Set<String> messagesFor(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
