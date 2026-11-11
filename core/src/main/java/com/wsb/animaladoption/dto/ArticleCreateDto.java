package com.wsb.animaladoption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ArticleCreateDto {
    @NotBlank(message = "{validation.title.required}")
    @Size(min = 5, max = 200, message = "{validation.title.size}")
    private String title;

    @NotBlank(message = "{validation.article.content.required}")
    @Size(min = 50, message = "{validation.article.content.size}")
    private String content;

    private MultipartFile imageFile;
}
