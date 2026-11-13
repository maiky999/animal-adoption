package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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

    /**
     * "My profile" link in the navigation. The logged-in user is known only by email (not by id), so instead of
     * looking up the id on every page, the link points here and the id is looked up only after a click.
     * /users/** is public, so a guest (no authentication) is sent to the login page.
     */
    @GetMapping("/me")
    public String showOwnPublicProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        User user = userService.findByEmail(authentication.getName());
        return "redirect:/users/" + user.getId();
    }

    @GetMapping("/{id}")
    public String showPublicProfile(@PathVariable Long id, Model model) {
        User user = userService.findById(id);

        List<Ad> activeAds = adService.findActiveAdsByUserId(id);

        model.addAttribute("profileUser", user);
        model.addAttribute("ads", activeAds);
        // Upper limit of the "ban for N hours" field in the moderation panel
        model.addAttribute("maxBanHours", UserService.MAX_BAN_HOURS);
        // Options of the "role" field in the administrator panel
        model.addAttribute("roles", RoleEnum.values());

        return "user/public-profile";
    }
}
