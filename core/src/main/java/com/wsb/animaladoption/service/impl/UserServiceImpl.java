package com.wsb.animaladoption.service.impl;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.dto.VerificationResult;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.VerificationResultEnum;
import com.wsb.animaladoption.event.EmailChangeEvent;
import com.wsb.animaladoption.event.RegistrationEvent;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.model.VerificationToken;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.repository.VerificationTokenRepository;
import com.wsb.animaladoption.service.FileStorageService;
import com.wsb.animaladoption.service.UserService;
import com.wsb.translations.TranslationService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
}
