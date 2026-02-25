package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.service.AdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final AdService adService;

    @GetMapping("/")
    public String home(Model model) {
        List<Ad> allActiveAds = adService.findActiveAds(null);
        List<Ad> latestAds = allActiveAds.stream()
                .limit(4)
                .toList();

        model.addAttribute("latestAds", latestAds);

        return "index";
    }
}
