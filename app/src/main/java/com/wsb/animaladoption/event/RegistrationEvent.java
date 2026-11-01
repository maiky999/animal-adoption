package com.wsb.animaladoption.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationEvent implements Serializable {
    private String email;
    private String verificationToken;
    /** Język, w którym ma zostać wysłany e-mail (np. "pl", "en"). */
    private String languageTag;

}
