package com.wsb.animaladoption.service.impl;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.dto.VerificationResult;
import com.wsb.animaladoption.enums.RoleEnum;
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
import com.wsb.animaladoption.service.FileStorageService;
import com.wsb.animaladoption.service.LoginAttemptService;
import com.wsb.animaladoption.service.UserService;
import com.wsb.translations.TranslationService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;
    private final FileStorageService fileStorageService;
    private final TranslationService translationService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final LoginAttemptService loginAttemptService;

    /** Minimum time between two reset emails for one account - protects the mailbox from being flooded. */
    static final Duration PASSWORD_RESET_COOLDOWN = Duration.ofMinutes(2);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    @Transactional
    public User registerNewUser(UserRegistrationDto dto) {
        User user = User.builder()
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .displayName(dto.getDisplayName())
                .mobile(dto.getMobile())
                .role(RoleEnum.USER)
                .userType(dto.getUserType())
                .build();

        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(token, user);
        tokenRepository.save(verificationToken);

        RegistrationEvent event = new RegistrationEvent(user.getEmail(), token,
                translationService.getCurrentLocale().toLanguageTag());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EMAIL, RabbitMQConfig.ROUTING_KEY_REGISTRATION, event);

        return user;
    }

    @Override
    @Transactional
    public VerificationResult verifyEmail(String token) {
        Optional<VerificationToken> tokenOptional = tokenRepository.findByToken(token);
        if (tokenOptional.isEmpty() || !tokenOptional.get().getExpiryDate().isAfter(LocalDateTime.now())) {
            return VerificationResult.invalid();
        }

        VerificationToken verificationToken = tokenOptional.get();
        User user = verificationToken.getUser();
        String previousEmail = user.getEmail();
        VerificationResultEnum status;

        if (user.getPendingEmail() != null) {
            // Link from an email change - the new address is confirmed, so it becomes the login
            String newEmail = user.getPendingEmail();
            user.setPendingEmail(null);
            // Someone could have registered with this address after the change was requested
            if (userRepository.existsByEmail(newEmail)) {
                status = VerificationResultEnum.EMAIL_TAKEN;
            } else {
                user.setEmail(newEmail);
                status = VerificationResultEnum.EMAIL_CHANGED;
            }
        } else {
            // Link from the registration email
            user.setEmailVerified(true);
            status = VerificationResultEnum.ACCOUNT_VERIFIED;
        }

        userRepository.save(user);
        tokenRepository.delete(verificationToken);
        return new VerificationResult(status, previousEmail, user.getEmail());
    }

    @Override
    @Transactional
    public boolean updateUserProfile(String currentEmail, UserProfileUpdateDto dto) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));

        // Emails are stored normalized (like in CustomUserDetailsService), so the comparison and the check use that form
        String newEmail = dto.getEmail().trim().toLowerCase();
        boolean emailChangeRequested = !user.getEmail().equalsIgnoreCase(newEmail);
        if (emailChangeRequested) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new LocalizedException("profile.error.emailTaken");
            }
            // The login does not change yet - only after clicking the link sent to the new address
            user.setPendingEmail(newEmail);
        }

        user.setDisplayName(dto.getDisplayName());
        user.setMobile(dto.getMobile());
        user.setDescription(dto.getDescription());

        if (dto.getAvatarFile() != null && !dto.getAvatarFile().isEmpty()) {
            // Save the new file first - if it is rejected (e.g. not an image), the old avatar stays untouched
            String avatarPath = fileStorageService.storeFile(dto.getAvatarFile());
            if (user.getAvatarUrl() != null) {
                fileStorageService.deleteFile(user.getAvatarUrl());
            }
            user.setAvatarUrl(avatarPath);
        }

        userRepository.save(user);

        if (emailChangeRequested) {
            sendEmailChangeConfirmation(user, newEmail);
        }
        return emailChangeRequested;
    }

    /** Creates (or renews) the user's verification token and sends the confirmation link to the new address. */
    private void sendEmailChangeConfirmation(User user, String newEmail) {
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = tokenRepository.findByUser(user)
                .orElseGet(() -> new VerificationToken(token, user));
        verificationToken.renew(token);
        tokenRepository.save(verificationToken);

        EmailChangeEvent event = new EmailChangeEvent(newEmail, token,
                translationService.getCurrentLocale().toLanguageTag());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EMAIL, RabbitMQConfig.ROUTING_KEY_EMAIL_CHANGE, event);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public void changeUserPassword(String email, UserPasswordUpdateDto dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new LocalizedException("profile.error.wrongPassword");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);
        // An unconfirmed account first has to be activated with the registration link (and it may soon be deleted
        // by the cleanup job). In both "no" cases we simply do nothing - the caller always shows the same message.
        if (userOptional.isEmpty() || !userOptional.get().isEmailVerified()) {
            return;
        }
        User user = userOptional.get();

        Optional<PasswordResetToken> existing = passwordResetTokenRepository.findByUser(user);
        if (existing.isPresent()
                && existing.get().getCreatedAt().isAfter(LocalDateTime.now().minus(PASSWORD_RESET_COOLDOWN))) {
            // A link was sent a moment ago - it is still valid, so we do not send another one
            return;
        }

        String token = generateResetToken();
        PasswordResetToken resetToken = existing.orElseGet(() -> new PasswordResetToken(hashToken(token), user));
        resetToken.renew(hashToken(token));
        passwordResetTokenRepository.save(resetToken);

        PasswordResetEvent event = new PasswordResetEvent(user.getEmail(), token,
                translationService.getCurrentLocale().toLanguageTag());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EMAIL, RabbitMQConfig.ROUTING_KEY_PASSWORD_RESET, event);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPasswordResetTokenValid(String token) {
        return findValidResetToken(token).isPresent();
    }

    @Override
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> resetToken = findValidResetToken(token);
        if (resetToken.isEmpty()) {
            return false;
        }

        User user = resetToken.get().getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // One-time link - deleted right after use
        passwordResetTokenRepository.delete(resetToken.get());
        // The user has just proven they own the mailbox, so a possible lock after wrong passwords is lifted
        loginAttemptService.loginSucceeded(user.getEmail());
        // "Remember me" cookies issued earlier stop working on their own - their signature contains the password hash
        return true;
    }

    @Override
    @Transactional
    public int deleteExpiredPasswordResetTokens() {
        return passwordResetTokenRepository.deleteExpired(LocalDateTime.now());
    }

    private Optional<PasswordResetToken> findValidResetToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return passwordResetTokenRepository.findByTokenHash(hashToken(token))
                .filter(t -> !t.isExpired());
    }

    /** 32 random bytes (256 bits) from a cryptographically secure generator, written in a URL-safe form. */
    private static String generateResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 of the token. A plain (fast) hash is enough here - unlike passwords, the token is long and random,
     * so it cannot be guessed by trying many values, and BCrypt would only slow down the lookup.
     */
    public static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java implementation is required to support SHA-256
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Transactional
    public int deleteExpiredUnverifiedAccounts() {
        List<VerificationToken> expiredTokens = tokenRepository.findExpiredOfUnverifiedUsers(LocalDateTime.now());
        // An unconfirmed account cannot log in, so it has no listings, messages or avatar - only the token and the user.
        // The token is deleted first, because it references the user (foreign key).
        List<User> users = expiredTokens.stream().map(VerificationToken::getUser).toList();
        tokenRepository.deleteAll(expiredTokens);
        userRepository.deleteAll(users);
        return users.size();
    }
}
