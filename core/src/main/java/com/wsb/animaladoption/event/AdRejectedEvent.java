package com.wsb.animaladoption.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Notification for the author that a moderator rejected their listing, together with the reason. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdRejectedEvent implements Serializable {
    private String authorEmail;
    private String authorName;
    private String adTitle;
    private String reason;
    /** Language in which the email should be sent (e.g. "pl", "en"). */
    private String languageTag;
}
