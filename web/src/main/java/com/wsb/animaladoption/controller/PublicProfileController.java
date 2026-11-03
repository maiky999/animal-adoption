package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class PublicProfileController {
    private final UserService userService;
    private final AdService adService;

    @GetMapping("/{id}")
    public String showPublicProfile(@PathVariable Long id, Model model) {
        User user = userService.findById(id);

        List<Ad> activeAds = adService.findActiveAdsByUserId(id);

        model.addAttribute("profileUser", user);
        model.addAttribute("ads", activeAds);

        return "user/public-profile";
    }
}
