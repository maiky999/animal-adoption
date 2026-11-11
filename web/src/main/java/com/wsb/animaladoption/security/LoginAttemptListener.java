package com.wsb.animaladoption.security;

import com.wsb.animaladoption.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Listens to Spring Security login events and passes them to LoginAttemptService.
 * The events are published by the AuthenticationEventPublisher defined in SecurityConfig.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptListener {
    private final LoginAttemptService loginAttemptService;

    /** Wrong password (or a non-existent account - Spring Security reports both the same way). */
    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        loginAttemptService.loginFailed(email);
        if (loginAttemptService.isBlocked(email)) {
            log.warn("Login temporarily blocked after too many failed attempts: {}", email);
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        loginAttemptService.loginSucceeded(event.getAuthentication().getName());
    }
}
