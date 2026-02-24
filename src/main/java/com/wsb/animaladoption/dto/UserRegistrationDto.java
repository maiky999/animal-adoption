package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Singular;

@Data
@Getter
public class UserRegistrationDto {
    @NotBlank(message = "Adres email jest wymagany")
    @Email(message = "Podano niepoprawny format adresu email")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 8, message = "Hasło musi zawierać co najmniej 8 znaków")
    private String password;

    @NotBlank(message = "Nazwa wyświetlana jest wymagana")
    @Size(max = 100, message = "Nazwa wyświetlana nie może przekraczać 100 znaków")
    private String displayName;

    @NotBlank(message = "Numer telefonu jest wymagany")
    @Pattern(regexp = "^\\d{9}$", message = "Numer telefonu musi składać się dokładnie z 9 cyfr")
    private String mobile;
}
