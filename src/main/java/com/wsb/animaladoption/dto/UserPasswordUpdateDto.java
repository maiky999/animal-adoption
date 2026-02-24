package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPasswordUpdateDto {
    @NotBlank(message = "Obecne hasło jest wymagane")
    private String currentPassword;

    @NotBlank(message = "Nowe hasło jest wymagane")
    @Size(min = 8, message = "Nowe hasło musi mieć co najmniej 8 znaków")
    private String newPassword;
}
