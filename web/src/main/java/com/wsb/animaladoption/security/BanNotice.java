package com.wsb.animaladoption.security;

import com.wsb.animaladoption.model.User;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Information about a ban shown on the login page: why and until when the account is blocked.
 * Passed through the session, because the login page is reached by a redirect
 * (after a failed login, or after a logged-in user was blocked in the meantime).
 *
 * @param reason      reason given by the moderator
 * @param bannedUntil end of the ban
 */
public record BanNotice(String reason, LocalDateTime bannedUntil) implements Serializable {

    /** Name of the session attribute - read (and removed) by AuthController when it shows the login page. */
    public static final String SESSION_ATTRIBUTE = "BAN_NOTICE";

    /** Redirect target that makes the login page show the ban message. */
    public static final String LOGIN_URL = "/login?banned";

    public static void store(HttpServletRequest request, User bannedUser) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE,
                new BanNotice(bannedUser.getBanReason(), bannedUser.getBannedUntil()));
    }
}
