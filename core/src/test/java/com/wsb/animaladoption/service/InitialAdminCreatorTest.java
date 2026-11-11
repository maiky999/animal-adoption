package com.wsb.animaladoption.service;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitialAdminCreatorTest {

    private static final String STRONG_PASSWORD = "Bardzo-Dlugie-Haslo-2026";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private InitialAdminCreator creator(String email, String password) {
        return new InitialAdminCreator(userRepository, passwordEncoder, email, password);
    }

    @Test
    @DisplayName("Without INITIAL_ADMIN_EMAIL nothing happens")
    void shouldDoNothingWhenNotConfigured() {
        creator("", "").run(null);
        creator(null, null).run(null);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("When an administrator already exists, the configuration is ignored and no account is touched")
    void shouldDoNothingWhenAdminExists() {
        when(userRepository.existsByRole(RoleEnum.ADMIN)).thenReturn(true);

        creator("admin@wsb.pl", STRONG_PASSWORD).run(null);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Creates a verified administrator account with an encoded password")
    void shouldCreateAdmin() {
        when(userRepository.existsByRole(RoleEnum.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@wsb.pl")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("hash");

        // the email is normalized just like at registration
        creator("  Admin@WSB.pl ", STRONG_PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User admin = saved.getValue();
        assertEquals("admin@wsb.pl", admin.getEmail());
        assertEquals("hash", admin.getPasswordHash());
        assertEquals(RoleEnum.ADMIN, admin.getRole());
        assertEquals(UserTypeEnum.PRIVATE, admin.getUserType());
        assertTrue(admin.isEmailVerified());
    }

    @Test
    @DisplayName("An existing account is promoted, and its password stays the same")
    void shouldPromoteExistingAccount() {
        User existing = User.builder().id(5L).email("admin@wsb.pl").passwordHash("own-hash")
                .role(RoleEnum.USER).isEmailVerified(false)
                .bannedUntil(LocalDateTime.now().plusDays(1)).banReason("Spam").build();
        when(userRepository.existsByRole(RoleEnum.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@wsb.pl")).thenReturn(Optional.of(existing));

        creator("admin@wsb.pl", STRONG_PASSWORD).run(null);

        assertEquals(RoleEnum.ADMIN, existing.getRole());
        assertEquals("own-hash", existing.getPasswordHash());
        assertTrue(existing.isEmailVerified());
        assertFalse(existing.isBanned());
        verify(userRepository).save(existing);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("A too short password stops the start instead of creating a weak administrator account")
    void shouldRejectWeakPassword() {
        when(userRepository.existsByRole(RoleEnum.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@wsb.pl")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> creator("admin@wsb.pl", "krotkie").run(null));
        assertThrows(IllegalStateException.class, () -> creator("admin@wsb.pl", "").run(null));
        verify(userRepository, never()).save(any());
    }
}
