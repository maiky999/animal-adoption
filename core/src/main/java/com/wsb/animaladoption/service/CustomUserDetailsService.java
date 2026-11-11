package com.wsb.animaladoption.service;

import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElseThrow(() ->
                new UsernameNotFoundException("Nie znaleziono konta powiązanego z emailem: " + normalizedEmail));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.isEmailVerified(),
                // accountNonExpired = false for an account blocked by a moderator. Spring Security then rejects
                // the login (and "remember me") with AccountExpiredException - we use this flag for bans, because
                // the "locked" flag is already used for wrong passwords, and the user must see a different message
                // (LoginFailureHandler shows the reason and the end of the ban).
                !user.isBanned(),
                true,
                // A locked account is rejected by Spring Security BEFORE the password is checked (LockedException),
                // so during the lock even the correct password does not help - guessing makes no sense
                !loginAttemptService.isBlocked(normalizedEmail),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
