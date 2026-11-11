package com.wsb.animaladoption.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Keeps the logged-in user's session consistent with the database after their login (email) changes.
 * <p>
 * Spring Security remembers the user by email - both in the session and in the "remember me" cookie.
 * When the email changes, both still contain the old address, so every following operation
 * would look for an account that no longer exists.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationUpdater {
    private static final String REMEMBER_ME_COOKIE = "remember-me";

    private final UserDetailsService userDetailsService;
    private final TokenBasedRememberMeServices rememberMeServices;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /**
     * Logs the current user in again under the new email: replaces the authentication in the session
     * and, if the user used "remember me", issues a new cookie with the new email.
     * <p>
     * The principal must be a full {@link UserDetails} object (not just a String with the email),
     * because controllers read it with {@code @AuthenticationPrincipal UserDetails}.
     */
    public void refresh(String newEmail, HttpServletRequest request, HttpServletResponse response) {
        UserDetails updatedUser = userDetailsService.loadUserByUsername(newEmail);
        Authentication newAuth = UsernamePasswordAuthenticationToken.authenticated(
                updatedUser, null, updatedUser.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);
        // Since Spring Security 6 the context is not saved to the session automatically - we have to do it ourselves
        securityContextRepository.saveContext(context, request, response);

        if (WebUtils.getCookie(request, REMEMBER_ME_COOKIE) != null) {
            rememberMeServices.onLoginSuccess(request, response, newAuth);
        }
    }

    /**
     * Logs the user out: clears the session and the "remember me" cookie.
     * Used when the session belongs to an email that no longer exists (e.g. changed on another device).
     */
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        rememberMeServices.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}
