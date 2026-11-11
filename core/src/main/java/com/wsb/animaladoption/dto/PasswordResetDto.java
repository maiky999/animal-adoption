package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Form for setting a new password from the link sent by email. */
@Data
public class PasswordResetDto {
    /** Token from the link - sent back in a hidden field. */
    @NotBlank
    private String token;

    @NotBlank(message = "{validation.password.new.required}")
    @Size(min = 8, message = "{validation.password.new.size}")
    private String newPassword;

    private String confirmPassword;

    /** Typing the password twice protects against a typo in a field the user cannot see. */
    @AssertTrue(message = "{validation.password.mismatch}")
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
