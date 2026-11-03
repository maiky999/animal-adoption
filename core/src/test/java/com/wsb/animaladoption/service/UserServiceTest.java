package com.wsb.animaladoption.service;

import com.wsb.animaladoption.service.impl.UserServiceImpl;
import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.event.RegistrationEvent;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.model.VerificationToken;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.repository.VerificationTokenRepository;
import com.wsb.translations.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@wsb.pl")
                .passwordHash("encodedPassword")
                .displayName("Jan Tester")
                .mobile("123456789")
                .role(RoleEnum.USER)
                .isEmailVerified(false)
                .build();
    }

    @Test
    @DisplayName("Should register the user and send a registration event")
    void shouldRegisterNewUser() {
        UserRegistrationDto dto = new UserRegistrationDto();
        dto.setEmail("nowy@wsb.pl");
        dto.setPassword("haslo123");
        dto.setDisplayName("Nowy User");
        dto.setMobile("987654321");
        dto.setUserType(UserTypeEnum.PRIVATE);

        when(passwordEncoder.encode("haslo123")).thenReturn("encodedPassword");
        when(translationService.getCurrentLocale()).thenReturn(Locale.ENGLISH);

        User registered = userService.registerNewUser(dto);

        assertNotNull(registered);
        assertEquals("nowy@wsb.pl", registered.getEmail());
        assertEquals(RoleEnum.USER, registered.getRole());

        verify(userRepository, times(1)).save(any(User.class));
        verify(tokenRepository, times(1)).save(any(VerificationToken.class));
        ArgumentCaptor<RegistrationEvent> eventCaptor = ArgumentCaptor.forClass(RegistrationEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_REGISTRATION),
                eventCaptor.capture()
        );
        // The verification email should be sent in the language the user registered in
        assertEquals("en", eventCaptor.getValue().getLanguageTag());
    }

    @Test
    @DisplayName("Should verify the email when the token is valid")
    void shouldVerifyEmailSuccessfully() {
        String tokenStr = "valid-token-123";
        VerificationToken token = new VerificationToken(tokenStr, sampleUser);
        token.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(tokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));

        boolean result = userService.verifyEmail(tokenStr);

        assertTrue(result);
        assertTrue(sampleUser.isEmailVerified());
        verify(userRepository, times(1)).save(sampleUser);
        verify(tokenRepository, times(1)).delete(token);
    }

    @Test
    @DisplayName("Should not verify the email when the token has expired")
    void shouldNotVerifyEmailWhenTokenExpired() {
        String tokenStr = "expired-token-123";
        VerificationToken token = new VerificationToken(tokenStr, sampleUser);
        token.setExpiryDate(LocalDateTime.now().minusHours(1));

        when(tokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));

        boolean result = userService.verifyEmail(tokenStr);

        assertFalse(result);
        assertFalse(sampleUser.isEmailVerified());
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should change the user's password when the current password is correct")
    void shouldChangeUserPassword() {
        UserPasswordUpdateDto dto = new UserPasswordUpdateDto();
        dto.setCurrentPassword("stareHaslo");
        dto.setNewPassword("noweHaslo");

        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("stareHaslo", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("noweHaslo")).thenReturn("newEncodedPassword");

        userService.changeUserPassword("test@wsb.pl", dto);

        assertEquals("newEncodedPassword", sampleUser.getPasswordHash());
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    @DisplayName("Should throw an error when changing the password if the current password does not match")
    void shouldThrowExceptionWhenCurrentPasswordIsInvalid() {
        UserPasswordUpdateDto dto = new UserPasswordUpdateDto();
        dto.setCurrentPassword("zleHaslo");
        dto.setNewPassword("noweHaslo");

        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("zleHaslo", "encodedPassword")).thenReturn(false);

        LocalizedException exception = assertThrows(LocalizedException.class, () ->
                userService.changeUserPassword("test@wsb.pl", dto)
        );
        assertEquals("profile.error.wrongPassword", exception.getMessageKey());
        verify(userRepository, never()).save(any());
    }
}