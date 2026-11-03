package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPasswordUpdateDto {
    @NotBlank(message = "{validation.password.current.required}")
    private String currentPassword;

    @NotBlank(message = "{validation.password.new.required}")
    @Size(min = 8, message = "{validation.password.new.size}")
    private String newPassword;
}
