package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class AdCreateDto {
    @NotBlank(message = "{validation.title.required}")
    @Size(min = 10, max = 150, message = "{validation.title.size}")
    private String title;

    @NotBlank(message = "{validation.description.required}")
    @Size(min = 20, message = "{validation.ad.description.size}")
    private String description;

    @NotNull(message = "{validation.category.required}")
    private Long categoryId;

    @NotBlank(message = "{validation.location.required}")
    private String location;

    private List<MultipartFile> imageFiles;
}
