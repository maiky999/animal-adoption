package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.service.AdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/moderation")
@RequiredArgsConstructor
public class ModerationController {
    /** Maximum length of the rejection reason (the same as the database column). */
    static final int MAX_REASON_LENGTH = 500;

    private final AdService adService;

    @GetMapping("/ads")
    public String showPendingAds(Model model) {
        model.addAttribute("pendingAds", adService.findPendingAds());
        return "moderation/list";
    }

    @PostMapping("/ads/{id}/approve")
    public String approveAd(@PathVariable Long id) {
        adService.approveAd(id);
        return "redirect:/moderation/ads";
    }

    @PostMapping("/ads/{id}/reject")
    public String rejectAd(@PathVariable Long id,
                           @RequestParam(name = "reason", required = false) String reason,
                           RedirectAttributes redirectAttributes) {
        // The author has to know what to fix, so a rejection without a reason is not accepted
        if (reason == null || reason.isBlank() || reason.trim().length() > MAX_REASON_LENGTH) {
            redirectAttributes.addFlashAttribute("errorMessage", "moderation.ads.reject.reasonInvalid");
            return "redirect:/moderation/ads";
        }
        adService.rejectAd(id, reason);
        return "redirect:/moderation/ads";
    }
}
