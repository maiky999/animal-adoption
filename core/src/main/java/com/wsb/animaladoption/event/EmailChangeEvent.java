package com.wsb.animaladoption.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Request to send a confirmation link to the new email address the user entered in their profile. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailChangeEvent implements Serializable {
    /** The new (not yet confirmed) address - the email is sent there. */
    private String newEmail;
    private String verificationToken;
    /** Language in which the email should be sent (e.g. "pl", "en"). */
    private String languageTag;
}
