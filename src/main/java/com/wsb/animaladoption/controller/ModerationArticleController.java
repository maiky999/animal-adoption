package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.ArticleCreateDto;
import com.wsb.animaladoption.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/moderation/articles")
@RequiredArgsConstructor
public class ModerationArticleController {
    private final ArticleService articleService;

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("articleCreateDto", new ArticleCreateDto());
        return "moderation/article-form";
    }

    @PostMapping("/new")
    public String createArticle(@Valid @ModelAttribute("articleCreateDto") ArticleCreateDto dto,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal UserDetails currentUser,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "moderation/article-form";
        }

        articleService.createArticle(dto, currentUser.getUsername());
        redirectAttributes.addFlashAttribute("infoMessage", "Artykuł został pomyślnie opublikowany!");

        return "redirect:/articles";
    }
}