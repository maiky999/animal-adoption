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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
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

    @Test
    @DisplayName("Too many photos - the message should contain the limit from AdCreateDto.MAX_IMAGES")
    void shouldTranslateTooManyImagesMessage() {
        LocaleContextHolder.setLocale(Locale.of("pl"));
        AdCreateDto dto = new AdCreateDto();
        dto.setImageFiles(Collections.<MultipartFile>nCopies(AdCreateDto.MAX_IMAGES + 1,
                new MockMultipartFile("imageFiles", "zdjecie.jpg", "image/jpeg", new byte[]{1})));

        assertThat(messagesFor(dto)).contains("Można dodać maksymalnie 10 zdjęć");
    }

    private Set<String> messagesFor(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
