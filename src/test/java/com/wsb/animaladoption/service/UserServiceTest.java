package com.wsb.animaladoption.service;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.event.RegistrationEvent;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.model.VerificationToken;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
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

    @InjectMocks
    private UserService userService;

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
    @DisplayName("Powinien poprawnie zarejestrować użytkownika i wysłać event rejestracji")
    void shouldRegisterNewUser() {
        UserRegistrationDto dto = new UserRegistrationDto();
        dto.setEmail("nowy@wsb.pl");
        dto.setPassword("haslo123");
        dto.setDisplayName("Nowy User");
        dto.setMobile("987654321");
        dto.setUserType(UserTypeEnum.PRIVATE);

        when(passwordEncoder.encode("haslo123")).thenReturn("encodedPassword");

        User registered = userService.registerNewUser(dto);

        assertNotNull(registered);
        assertEquals("nowy@wsb.pl", registered.getEmail());
        assertEquals(RoleEnum.USER, registered.getRole());

        verify(userRepository, times(1)).save(any(User.class));
        verify(tokenRepository, times(1)).save(any(VerificationToken.class));
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_REGISTRATION),
                any(RegistrationEvent.class)
        );
    }

    @Test
    @DisplayName("Powinien zweryfikować email gdy token jest ważny")
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
    @DisplayName("Nie powinien zweryfikować emaila gdy token wygasł")
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
    @DisplayName("Powinien zmienić hasło użytkownika gdy obecne hasło jest poprawne")
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
    @DisplayName("Powinien rzucić błąd przy zmianie hasła gdy obecne hasło się nie zgadza")
    void shouldThrowExceptionWhenCurrentPasswordIsInvalid() {
        UserPasswordUpdateDto dto = new UserPasswordUpdateDto();
        dto.setCurrentPassword("zleHaslo");
        dto.setNewPassword("noweHaslo");

        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("zleHaslo", "encodedPassword")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                userService.changeUserPassword("test@wsb.pl", dto)
        );
        verify(userRepository, never()).save(any());
    }
}