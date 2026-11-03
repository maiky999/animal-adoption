package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.service.AdService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/my-ads")
@RequiredArgsConstructor
public class UserController {
    private final AdService adService;
    @GetMapping
    public String getMyAds(@AuthenticationPrincipal UserDetails currentUser, Model model) {
        List<Ad> ads = adService.findUserAds(currentUser.getUsername());
        model.addAttribute("ads", ads);
        return "user/my-ads";
    }

    @PostMapping("/{id}/status")
    public String changeAdStatus(@PathVariable Long id,
                                 @RequestParam("status") AdStatusEnum status,
                                 @AuthenticationPrincipal UserDetails currentUser) {
        adService.changeAdStatus(id, status, currentUser.getUsername());
        return "redirect:/my-ads";
    }
}
