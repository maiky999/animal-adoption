package com.wsb.animaladoption.service;

import com.wsb.animaladoption.service.impl.UserServiceImpl;
import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.dto.VerificationResult;
import com.wsb.animaladoption.enums.AccountStateEnum;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.enums.VerificationResultEnum;
import com.wsb.animaladoption.event.EmailChangeEvent;
import com.wsb.animaladoption.event.PasswordResetEvent;
import com.wsb.animaladoption.event.RegistrationEvent;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.model.PasswordResetToken;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.model.VerificationToken;
import com.wsb.animaladoption.repository.PasswordResetTokenRepository;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.repository.VerificationTokenRepository;
import com.wsb.translations.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
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

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AdService adService;

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

        VerificationResult result = userService.verifyEmail(tokenStr);

        assertEquals(VerificationResultEnum.ACCOUNT_VERIFIED, result.status());
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

        VerificationResult result = userService.verifyEmail(tokenStr);

        assertEquals(VerificationResultEnum.INVALID, result.status());
        assertFalse(sampleUser.isEmailVerified());
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should not change the email immediately, but store it as pending and send a confirmation link")
    void shouldStoreNewEmailAsPendingAndSendConfirmation() {
        UserProfileUpdateDto dto = new UserProfileUpdateDto();
        dto.setEmail(" Nowy@WSB.pl ");
        dto.setDisplayName("Jan Tester");
        dto.setMobile("123456789");

        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByEmail("nowy@wsb.pl")).thenReturn(false);
        when(tokenRepository.findByUser(sampleUser)).thenReturn(Optional.empty());
        when(translationService.getCurrentLocale()).thenReturn(Locale.of("pl"));

        boolean emailChangePending = userService.updateUserProfile("test@wsb.pl", dto);

        assertTrue(emailChangePending);
        assertEquals("test@wsb.pl", sampleUser.getEmail(), "login nie może się zmienić przed potwierdzeniem");
        assertEquals("nowy@wsb.pl", sampleUser.getPendingEmail());
        verify(tokenRepository, times(1)).save(any(VerificationToken.class));

        ArgumentCaptor<EmailChangeEvent> eventCaptor = ArgumentCaptor.forClass(EmailChangeEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL_CHANGE), eventCaptor.capture());
        assertEquals("nowy@wsb.pl", eventCaptor.getValue().getNewEmail());
    }

    @Test
    @DisplayName("Should switch to the pending email after the confirmation link is clicked")
    void shouldChangeEmailWhenPendingEmailConfirmed() {
        sampleUser.setEmailVerified(true);
        sampleUser.setPendingEmail("nowy@wsb.pl");
        VerificationToken token = new VerificationToken("change-token", sampleUser);

        when(tokenRepository.findByToken("change-token")).thenReturn(Optional.of(token));
        when(userRepository.existsByEmail("nowy@wsb.pl")).thenReturn(false);

        VerificationResult result = userService.verifyEmail("change-token");

        assertEquals(VerificationResultEnum.EMAIL_CHANGED, result.status());
        assertEquals("test@wsb.pl", result.previousEmail());
        assertEquals("nowy@wsb.pl", result.currentEmail());
        assertEquals("nowy@wsb.pl", sampleUser.getEmail());
        assertNull(sampleUser.getPendingEmail());
        verify(tokenRepository).delete(token);
    }

    @Test
    @DisplayName("Should cancel the email change when the new address was taken in the meantime")
    void shouldNotChangeEmailWhenTakenInTheMeantime() {
        sampleUser.setPendingEmail("zajety@wsb.pl");
        VerificationToken token = new VerificationToken("change-token", sampleUser);

        when(tokenRepository.findByToken("change-token")).thenReturn(Optional.of(token));
        when(userRepository.existsByEmail("zajety@wsb.pl")).thenReturn(true);

        VerificationResult result = userService.verifyEmail("change-token");

        assertEquals(VerificationResultEnum.EMAIL_TAKEN, result.status());
        assertEquals("test@wsb.pl", sampleUser.getEmail());
        assertNull(sampleUser.getPendingEmail());
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

    @Test
    @DisplayName("Should delete unconfirmed accounts with an expired activation link (token first, then the user)")
    void shouldDeleteExpiredUnverifiedAccounts() {
        User unverified = User.builder().id(2L).email("bot@example.com").build();
        VerificationToken expiredToken = new VerificationToken("expired-token", unverified);
        when(tokenRepository.findExpiredOfUnverifiedUsers(any(LocalDateTime.class))).thenReturn(List.of(expiredToken));

        int deleted = userService.deleteExpiredUnverifiedAccounts();

        assertEquals(1, deleted);
        InOrder inOrder = inOrder(tokenRepository, userRepository);
        inOrder.verify(tokenRepository).deleteAll(List.of(expiredToken));
        inOrder.verify(userRepository).deleteAll(List.of(unverified));
    }

    // --- Forgot password ---

    @Test
    @DisplayName("Password reset: sends a link to a confirmed account and stores only the token hash")
    void shouldSendPasswordResetLink() {
        sampleUser.setEmailVerified(true);
        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));
        when(passwordResetTokenRepository.findByUser(sampleUser)).thenReturn(Optional.empty());
        when(translationService.getCurrentLocale()).thenReturn(Locale.of("pl"));

        userService.requestPasswordReset("  Test@WSB.pl ");

        ArgumentCaptor<PasswordResetEvent> eventCaptor = ArgumentCaptor.forClass(PasswordResetEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_PASSWORD_RESET), eventCaptor.capture());
        String tokenFromEmail = eventCaptor.getValue().getResetToken();
        assertEquals("test@wsb.pl", eventCaptor.getValue().getEmail());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        // The database gets the hash, never the token from the email
        assertNotEquals(tokenFromEmail, tokenCaptor.getValue().getTokenHash());
        assertEquals(UserServiceImpl.hashToken(tokenFromEmail), tokenCaptor.getValue().getTokenHash());
    }

    @Test
    @DisplayName("Password reset: an unknown email gets no link (and no error that would reveal it)")
    void shouldSilentlyIgnoreUnknownEmail() {
        when(userRepository.findByEmail("nieznany@wsb.pl")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> userService.requestPasswordReset("nieznany@wsb.pl"));

        verifyNoInteractions(rabbitTemplate, passwordResetTokenRepository);
    }

    @Test
    @DisplayName("Password reset: an unconfirmed account gets no link")
    void shouldNotSendResetLinkToUnverifiedAccount() {
        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));

        userService.requestPasswordReset("test@wsb.pl");

        verifyNoInteractions(rabbitTemplate, passwordResetTokenRepository);
    }

    @Test
    @DisplayName("Password reset: a second request within the cooldown does not send another email")
    void shouldNotResendResetLinkWithinCooldown() {
        sampleUser.setEmailVerified(true);
        PasswordResetToken recent = new PasswordResetToken("hash", sampleUser);
        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));
        when(passwordResetTokenRepository.findByUser(sampleUser)).thenReturn(Optional.of(recent));

        userService.requestPasswordReset("test@wsb.pl");

        verifyNoInteractions(rabbitTemplate);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Password reset: a valid link sets the new password, is deleted and lifts the login lock")
    void shouldResetPasswordWithValidToken() {
        PasswordResetToken token = new PasswordResetToken(UserServiceImpl.hashToken("abc"), sampleUser);
        when(passwordResetTokenRepository.findByTokenHash(UserServiceImpl.hashToken("abc"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("noweHaslo123")).thenReturn("newEncoded");

        boolean result = userService.resetPassword("abc", "noweHaslo123");

        assertTrue(result);
        assertEquals("newEncoded", sampleUser.getPasswordHash());
        verify(userRepository).save(sampleUser);
        verify(passwordResetTokenRepository).delete(token);
        verify(loginAttemptService).loginSucceeded("test@wsb.pl");
    }

    @Test
    @DisplayName("Password reset: an expired link does not change the password")
    void shouldRejectExpiredResetToken() {
        PasswordResetToken token = new PasswordResetToken(UserServiceImpl.hashToken("abc"), sampleUser);
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByTokenHash(UserServiceImpl.hashToken("abc"))).thenReturn(Optional.of(token));

        boolean result = userService.resetPassword("abc", "noweHaslo123");

        assertFalse(result);
        assertEquals("encodedPassword", sampleUser.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Password reset: an unknown or empty link is invalid")
    void shouldRejectUnknownResetToken() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertFalse(userService.isPasswordResetTokenValid("nieistniejacy"));
        assertFalse(userService.isPasswordResetTokenValid(null));
        assertFalse(userService.resetPassword("", "noweHaslo123"));
    }

    // --- Blocking accounts ---

    @Test
    @DisplayName("Ban: blocks the account for the given number of hours with the reason")
    void shouldBanUserForGivenHours() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(translationService.getDefaultLocale()).thenReturn(Locale.of("pl"));
        when(translationService.get(Locale.of("pl"), "moderation.ads.removedBecauseOfBan", "Spam w ogłoszeniach"))
                .thenReturn("Konto autora zostało zablokowane. Powód: Spam w ogłoszeniach");
        LocalDateTime before = LocalDateTime.now();

        userService.banUser(1L, "  Spam w ogłoszeniach ", 48);

        assertTrue(sampleUser.isBanned());
        assertEquals("Spam w ogłoszeniach", sampleUser.getBanReason());
        assertFalse(sampleUser.getBannedUntil().isBefore(before.plusHours(48)));
        assertTrue(sampleUser.getBannedUntil().isBefore(before.plusHours(48).plusMinutes(1)));
        verify(userRepository).save(sampleUser);
        // All listings of the blocked user are removed, with the ban reason
        verify(adService).removeAllAdsOfAuthor(1L, "Konto autora zostało zablokowane. Powód: Spam w ogłoszeniach");
    }

    @Test
    @DisplayName("Ban: moderators and administrators cannot be blocked")
    void shouldNotBanStaff() {
        User moderator = User.builder().id(2L).email("mod@wsb.pl").role(RoleEnum.MODERATOR).build();
        User admin = User.builder().id(3L).email("admin@wsb.pl").role(RoleEnum.ADMIN).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(moderator));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));

        assertThrows(LocalizedException.class, () -> userService.banUser(2L, "Powód", 24));
        assertThrows(LocalizedException.class, () -> userService.banUser(3L, "Powód", 24));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(adService);
    }

    @Test
    @DisplayName("Ban: a reason and a number of hours within the limit are required")
    void shouldValidateBanInput() {
        assertThrows(IllegalArgumentException.class, () -> userService.banUser(1L, " ", 24));
        assertThrows(IllegalArgumentException.class, () -> userService.banUser(1L, "Powód", 0));
        assertThrows(IllegalArgumentException.class, () -> userService.banUser(1L, "Powód", UserService.MAX_BAN_HOURS + 1));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unban: lifts the ban before it ends")
    void shouldUnbanUser() {
        sampleUser.setBannedUntil(LocalDateTime.now().plusDays(1));
        sampleUser.setBanReason("Powód");
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        userService.unbanUser(1L);

        assertFalse(sampleUser.isBanned());
        assertNull(sampleUser.getBanReason());
        // Removal is final - unblocking does not bring the listings back
        verifyNoInteractions(adService);
    }

    @Test
    @DisplayName("Account state: an expired ban no longer blocks the account")
    void shouldReportAccountState() {
        when(userRepository.findByEmail("test@wsb.pl")).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByEmail("brak@wsb.pl")).thenReturn(Optional.empty());

        sampleUser.setBannedUntil(LocalDateTime.now().plusHours(1));
        assertEquals(AccountStateEnum.BANNED, userService.getAccountState("test@wsb.pl"));
        assertTrue(userService.findActiveBan(" Test@WSB.pl ").isPresent());

        sampleUser.setBannedUntil(LocalDateTime.now().minusMinutes(1));
        assertEquals(AccountStateEnum.ACTIVE, userService.getAccountState("test@wsb.pl"));
        assertTrue(userService.findActiveBan("test@wsb.pl").isEmpty());

        assertEquals(AccountStateEnum.MISSING, userService.getAccountState("brak@wsb.pl"));
    }
}