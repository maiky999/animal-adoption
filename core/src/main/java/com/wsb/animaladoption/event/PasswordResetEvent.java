package com.wsb.animaladoption.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Request to send a "set a new password" link. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetEvent implements Serializable {
    private String email;
    /** The raw token - it exists only in this message and in the email; the database keeps just its hash. */
    private String resetToken;
    /** Language in which the email should be sent (e.g. "pl", "en"). */
    private String languageTag;
}
