package com.wsb.animaladoption.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Notification for the author that a moderator rejected or removed their listing, together with the reason.
 * One event (and one queue) for both cases - the emails differ only in the text.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdModerationEvent implements Serializable {

    public enum Action {
        /** Not approved - the author can correct the listing and send it again. */
        REJECTED,
        /** Removed (e.g. a published listing that breaks the rules) - final. */
        REMOVED
    }

    private Action action;
    private String authorEmail;
    private String authorName;
    private String adTitle;
    private String reason;
    /** Language in which the email should be sent (e.g. "pl", "en"). */
    private String languageTag;
}
