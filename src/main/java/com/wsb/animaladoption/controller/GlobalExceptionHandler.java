package com.wsb.animaladoption.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException exc, RedirectAttributes redirectAttributes) {

        redirectAttributes.addFlashAttribute("errorMessage",
                "Rozmiar przesłanych plików przekracza dopuszczalny limit serwera. Upewnij się, że zdjęcia nie są zbyt duże (maksymalnie 15MB/plik).");

        return "redirect:/ads/new";
    }
}
