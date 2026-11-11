package com.wsb.animaladoption.enums;

/** State of the account behind a logged-in session - checked on every request (StaleSessionInterceptor). */
public enum AccountStateEnum {
    /** The account exists and may be used. */
    ACTIVE,
    /** No account with this email any more (e.g. the email was changed on another device). */
    MISSING,
    /** Blocked by a moderator. */
    BANNED,
    /**
     * The account may be used, but an administrator changed its role after the user logged in -
     * the session still carries the old permissions and has to be refreshed.
     */
    ROLE_CHANGED
}
