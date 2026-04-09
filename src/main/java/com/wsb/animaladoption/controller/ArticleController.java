package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Article;
import com.wsb.animaladoption.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {
    private final ArticleService articleService;

    @GetMapping
    public String listArticles(Model model) {
        model.addAttribute("articles", articleService.findAll());
        return "articles/list";
    }

    @GetMapping("/{id}")
    public String viewArticle(@PathVariable Long id, Model model) {
        Article article = articleService.findById(id);
        model.addAttribute("article", article);
        return "articles/view";
    }
}
