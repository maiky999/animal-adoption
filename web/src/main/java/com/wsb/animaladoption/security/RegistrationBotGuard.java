package com.wsb.animaladoption.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Two cheap checks that catch simple bots on the registration form, without bothering real users:
 *
 * 1. Honeypot - the form has an extra field hidden from people (moved off-screen, skipped by the keyboard).
 *    A human never fills it in, but a simple bot fills in every field it finds.
 * 2. Fill-in time - when the form is displayed, the time is saved in the session. Nobody types an email,
 *    name, phone number and password in under MIN_FILL_TIME; a script sends the form immediately
 *    (or without displaying it at all - then there is no time in the session).
 */
@Component
public class RegistrationBotGuard {

    /** Name of the hidden field - an ordinary-sounding name, so that bots fill it in. */
    public static final String HONEYPOT_FIELD = "homepage";
    public static final String FORM_SHOWN_AT = "registrationFormShownAt";
    static final Duration MIN_FILL_TIME = Duration.ofSeconds(3);

    public enum Verdict {
        /** Looks like a human. */
        OK,
        /** The hidden field was filled in - certainly a bot. */
        HONEYPOT_FILLED,
        /** Sent too fast, or without displaying the form first (or the session expired) - the user can try again. */
        TOO_FAST
    }

    /** Called when the form is displayed - remembers the moment from which the fill-in time is counted. */
    public void markFormShown(HttpSession session) {
        session.setAttribute(FORM_SHOWN_AT, Instant.now());
    }

    public Verdict check(HttpSession session, String honeypotValue) {
        if (honeypotValue != null && !honeypotValue.isEmpty()) {
            return Verdict.HONEYPOT_FILLED;
        }
        Object shownAt = session.getAttribute(FORM_SHOWN_AT);
        if (!(shownAt instanceof Instant shownAtInstant)
                || Duration.between(shownAtInstant, Instant.now()).compareTo(MIN_FILL_TIME) < 0) {
            return Verdict.TOO_FAST;
        }
        return Verdict.OK;
    }
}
