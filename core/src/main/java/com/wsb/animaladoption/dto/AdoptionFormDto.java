package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AdoptionFormDto {
    @NotBlank(message = "{validation.firstName.required}")
    private String firstName;

    @NotBlank(message = "{validation.lastName.required}")
    private String lastName;

    @NotBlank(message = "{validation.address.required}")
    private String address;

    @NotBlank(message = "{validation.city.required}")
    private String city;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @NotBlank(message = "{validation.phone.required}")
    @Pattern(regexp = "^\\d{9}$", message = "{validation.phone.pattern}")
    private String phone;

    @NotNull(message = "{validation.birthYear.required}")
    @Min(value = 1900, message = "{validation.birthYear.min}")
    private Integer birthYear;

    @AssertTrue(message = "{validation.consent.required}")
    private boolean consent;
}