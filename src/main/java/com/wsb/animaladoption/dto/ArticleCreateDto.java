package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ArticleCreateDto {
    @NotBlank(message = "Tytuł jest wymagany")
    @Size(min = 5, max = 200, message = "Tytuł musi mieć od 5 do 200 znaków")
    private String title;

    @NotBlank(message = "Treść artykułu jest wymagana")
    @Size(min = 50, message = "Artykuł musi zawierać co najmniej 50 znaków")
    private String content;

    private MultipartFile imageFile;
}
