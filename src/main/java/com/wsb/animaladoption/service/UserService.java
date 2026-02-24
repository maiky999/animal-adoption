package com.wsb.animaladoption.service;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.event.RegistrationEvent;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public User registerNewUser(UserRegistrationDto dto) {
        User user = User.builder()
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .displayName(dto.getDisplayName())
                .mobile(dto.getMobile())
                .role(RoleEnum.USER)
                .build();

        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        RegistrationEvent event = new RegistrationEvent(user.getEmail(), token);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EMAIL, RabbitMQConfig.ROUTING_KEY_REGISTRATION, event);

        return user;
    }

    @Transactional
    public void updateUserProfile(String currentEmail, UserProfileUpdateDto dto) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));

        if (!user.getEmail().equalsIgnoreCase(dto.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("Ten adres email jest już powiązany z innym kontem.");
            }
            user.setEmail(dto.getEmail().trim().toLowerCase());
        }

        user.setDisplayName(dto.getDisplayName());
        user.setMobile(dto.getMobile());
        userRepository.save(user);
    }

    @Transactional
    public void changeUserPassword(String email, UserPasswordUpdateDto dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Obecne hasło jest nieprawidłowe.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }
}
