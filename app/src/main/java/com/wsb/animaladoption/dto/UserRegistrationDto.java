package com.wsb.animaladoption.dto;

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
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, message = "{validation.password.size}")
    private String password;

    @NotBlank(message = "{validation.displayName.required}")
    @Size(max = 100, message = "{validation.displayName.size}")
    private String displayName;

    @NotBlank(message = "{validation.phone.required}")
    @Pattern(regexp = "^\\d{9}$", message = "{validation.phone.pattern}")
    private String mobile;

    @NotNull(message = "{validation.userType.required}")
    private UserTypeEnum userType;
}
