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
 * Test integracyjny: komunikaty walidacji z DTO ({@code message = "{klucz}"}) powinny być tłumaczone
 * przez moduł translations na język bieżącego żądania, z uzupełnieniem wartości {min}/{max}.
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
    @DisplayName("Komunikaty walidacji powinny być po polsku dla języka polskiego")
    void shouldTranslateValidationMessagesToPolish() {
        LocaleContextHolder.setLocale(Locale.of("pl"));

        assertThat(messagesFor(new UserRegistrationDto())).contains("Adres email jest wymagany", "Hasło jest wymagane");
    }

    @Test
    @DisplayName("Komunikaty walidacji powinny być po angielsku dla języka angielskiego")
    void shouldTranslateValidationMessagesToEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(messagesFor(new UserRegistrationDto())).contains("Email address is required", "Password is required");
    }

    @Test
    @DisplayName("Powinien wstawić wartości {min} i {max} z adnotacji @Size")
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
