package com.wsb.animaladoption.dto;

import com.wsb.animaladoption.validation.NotDisposableEmail;
import com.wsb.animaladoption.enums.UserTypeEnum;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Getter;
import lombok.Singular;

@Data
@Getter
public class UserRegistrationDto {
    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @NotDisposableEmail
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, message = "{validation.password.size}")
    private String password;

    private String confirmPassword;

    @NotBlank(message = "{validation.displayName.required}")
    @Size(max = 100, message = "{validation.displayName.size}")
    private String displayName;

    @NotBlank(message = "{validation.phone.required}")
    @Pattern(regexp = "^\\d{9}$", message = "{validation.phone.pattern}")
    private String mobile;

    @NotNull(message = "{validation.userType.required}")
    private UserTypeEnum userType;

    /** Typing the password twice protects against a typo in a field the user cannot see (same as PasswordResetDto). */
    @AssertTrue(message = "{validation.password.mismatch}")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
