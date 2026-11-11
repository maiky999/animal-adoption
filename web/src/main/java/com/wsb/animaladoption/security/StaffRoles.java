package com.wsb.animaladoption.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;

/**
 * Roles that have access to the moderation features. ADMIN can do everything a MODERATOR can -
 * every check in Java uses this class, so the two roles cannot drift apart.
 * (Templates use the same pair in {@code sec:authorize="hasAnyRole('MODERATOR', 'ADMIN')"}.)
 */
public final class StaffRoles {

    /** Role names without the "ROLE_" prefix - the form expected by hasAnyRole(...). */
    public static final String[] NAMES = {"MODERATOR", "ADMIN"};

    private static final Set<String> AUTHORITIES = Set.of("ROLE_MODERATOR", "ROLE_ADMIN");

    private StaffRoles() {
    }

    /** Whether the logged-in user is a moderator or an administrator. */
    public static boolean isStaff(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && isStaff(authentication.getAuthorities());
    }

    /** Whether the authorities (roles) contain a moderator or administrator role. */
    public static boolean isStaff(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AUTHORITIES::contains);
    }
}
