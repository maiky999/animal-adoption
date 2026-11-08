package com.wsb.animaladoption.config;

import com.wsb.animaladoption.security.BanNotice;
import com.wsb.animaladoption.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Logs out sessions whose login (email) no longer exists in the database, or whose account has been blocked
 * by a moderator in the meantime.
 * <p>
 * Example: the user is logged in on a laptop and a phone, and confirms an email change on the phone.
 * The phone session is refreshed immediately ({@link SessionAuthenticationUpdater}), but the laptop session
 * still contains the old email - without this check every action there would fail with an error.
 * Instead the user is logged out and asked to log in again.
 */
@Component
@RequiredArgsConstructor
public class StaleSessionInterceptor implements HandlerInterceptor {
    private final UserService userService;
    private final SessionAuthenticationUpdater sessionAuthenticationUpdater;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);

        if (!loggedIn) {
            return true;
        }
        switch (userService.getAccountState(auth.getName())) {
            case MISSING -> {
                sessionAuthenticationUpdater.logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/login?expired");
                return false;
            }
            case BANNED -> {
                // Blocked by a moderator while logged in - the session ends right away, not only at the next login
                var bannedUser = userService.findActiveBan(auth.getName());
                sessionAuthenticationUpdater.logout(request, response, auth);
                // logout() invalidated the session, so the notice goes into a new one
                bannedUser.ifPresent(user -> BanNotice.store(request, user));
                response.sendRedirect(request.getContextPath() + BanNotice.LOGIN_URL);
                return false;
            }
            case ACTIVE -> {
                return true;
            }
        }
        return true;
    }
}
