package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AdoptionFormDto {
    @NotBlank(message = "Imię jest wymagane")
    private String firstName;

    @NotBlank(message = "Nazwisko jest wymagane")
    private String lastName;

    @NotBlank(message = "Ulica i numer są wymagane")
    private String address;

    @NotBlank(message = "Miasto jest wymagane")
    private String city;

    @NotBlank(message = "Adres email jest wymagany")
    @Email(message = "Podano niepoprawny format adresu email")
    private String email;

    @NotBlank(message = "Telefon jest wymagany")
    @Pattern(regexp = "^\\d{9}$", message = "Numer telefonu musi składać się z 9 cyfr")
    private String phone;

    @NotNull(message = "Rok urodzenia jest wymagany")
    @Min(value = 1900, message = "Podano niepoprawny rok")
    private Integer birthYear;

    @AssertTrue(message = "Musisz wyrazić zgodę na przetwarzanie danych")
    private boolean consent;
}