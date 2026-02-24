package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class AdCreateDto {
    @NotBlank(message = "Tytuł jest wymagany")
    @Size(min = 10, max = 150, message = "Tytuł musi mieć od 10 do 150 znaków")
    private String title;

    @NotBlank(message = "Opis jest wymagany")
    @Size(min = 20, message = "Opis musi mieć co najmniej 20 znaków")
    private String description;

    @NotNull(message = "Kategoria jest wymagana")
    private Long categoryId;

    @NotBlank(message = "Lokalizacja jest wymagana")
    private String location;

    private List<MultipartFile> imageFiles;
}
