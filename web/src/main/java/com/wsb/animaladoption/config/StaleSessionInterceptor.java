package com.wsb.animaladoption.config;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.security.BanNotice;
import com.wsb.animaladoption.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Logs out sessions whose login (email) no longer exists in the database, or whose account has been blocked
 * by a moderator in the meantime. Refreshes sessions whose role was changed by an administrator.
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
        switch (userService.getAccountState(auth.getName(), sessionRole(auth))) {
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
            case ROLE_CHANGED -> {
                // An administrator changed the role - the session gets the current permissions without logging out.
                // Access to this request was already decided (by Spring Security) with the OLD role, so instead of
                // handling it we send the browser to repeat it: a GET to the same address, anything else to home.
                sessionAuthenticationUpdater.refresh(auth.getName(), request, response);
                response.sendRedirect(repeatUrl(request));
                return false;
            }
            case ACTIVE -> {
                return true;
            }
        }
        return true;
    }

    /** The role saved in the session, e.g. "ROLE_MODERATOR" -> MODERATOR ({@code null} if there is none). */
    private static RoleEnum sessionRole(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    return RoleEnum.valueOf(name.substring("ROLE_".length()));
                } catch (IllegalArgumentException ignored) {
                    // not one of our roles - keep looking
                }
            }
        }
        return null;
    }

    private static String repeatUrl(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return request.getContextPath() + "/";
        }
        String query = request.getQueryString();
        return request.getRequestURI() + (query != null ? "?" + query : "");
    }
}
