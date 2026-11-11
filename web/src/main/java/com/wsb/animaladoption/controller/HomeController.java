package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final AdService adService;
    private final ArticleService articleService;

    @GetMapping("/")
    public String home(Model model) {
        List<Ad> allActiveAds = adService.findActiveAds(null);
        List<Ad> latestAds = allActiveAds.stream()
                .limit(4)
                .toList();

        model.addAttribute("latestAds", latestAds);
        model.addAttribute("latestArticles", articleService.findLatest(3));

        return "index";
    }
}
