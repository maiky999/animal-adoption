package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.service.AdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/moderation")
@RequiredArgsConstructor
public class ModerationController {
    private final AdService adService;

    @GetMapping("/ads")
    public String showPendingAds(Model model) {
        model.addAttribute("pendingAds", adService.findPendingAds());
        return "moderation/list";
    }

    @PostMapping("/ads/{id}/approve")
    public String approveAd(@PathVariable Long id) {
        adService.moderateAd(id, true);
        return "redirect:/moderation/ads";
    }

    @PostMapping("/ads/{id}/reject")
    public String rejectAd(@PathVariable Long id) {
        adService.moderateAd(id, false);
        return "redirect:/moderation/ads";
    }
}
