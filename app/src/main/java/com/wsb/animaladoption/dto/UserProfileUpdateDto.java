package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UserProfileUpdateDto {
    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @NotBlank(message = "{validation.displayName.required}")
    @Size(max = 100, message = "{validation.displayName.size}")
    private String displayName;

    @NotBlank(message = "{validation.phone.required}")
    @Pattern(regexp = "^\\d{9}$", message = "{validation.phone.pattern}")
    private String mobile;

    @Size(max = 500, message = "{validation.profile.description.size}")
    private String description;

    private MultipartFile avatarFile;
}
