package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.service.AdService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/** Moderation of listings. Available to moderators and administrators (see SecurityConfig, /moderation/**). */
@Controller
@RequestMapping("/moderation")
@RequiredArgsConstructor
public class ModerationController {
    /** Maximum length of a moderator's reason (the same as the database columns). */
    static final int MAX_REASON_LENGTH = 500;

    private final AdService adService;

    @GetMapping("/ads")
    public String showPendingAds(Model model) {
        model.addAttribute("pendingAds", adService.findPendingAds());
        return "moderation/list";
    }

    /** The "!" badge in the navigation - refreshed periodically (see layout/navbar.html). */
    @GetMapping("/pending-badge")
    public String pendingBadge() {
        return "layout/navbar :: pending-badge";
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
        if (!isValidReason(reason)) {
            redirectAttributes.addFlashAttribute("errorMessage", "moderation.ads.reject.reasonInvalid");
            return "redirect:/moderation/ads";
        }
        adService.rejectAd(id, reason);
        return "redirect:/moderation/ads";
    }

    /**
     * Removes a listing (usually a published one) from the listing details window.
     * Afterwards the moderator goes back to the page they came from (board, profile...).
     */
    @PostMapping("/ads/{id}/remove")
    public String removeAd(@PathVariable Long id,
                           @RequestParam(name = "reason", required = false) String reason,
                           HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {
        if (!isValidReason(reason)) {
            redirectAttributes.addFlashAttribute("moderationError", "moderation.ads.remove.reasonInvalid");
        } else {
            try {
                adService.removeAd(id, reason);
                redirectAttributes.addFlashAttribute("moderationMessage", "moderation.ads.remove.success");
            } catch (LocalizedException e) {
                redirectAttributes.addFlashAttribute("moderationError", e.getMessageKey());
            }
        }
        return "redirect:" + previousPage(request);
    }

    static boolean isValidReason(String reason) {
        return reason != null && !reason.isBlank() && reason.trim().length() <= MAX_REASON_LENGTH;
    }

    /**
     * The page the form was sent from (Referer header). Only its path is used - never the host -
     * so a forged header cannot redirect the moderator to another website.
     */
    static String previousPage(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getRawPath();
                if (path != null && path.startsWith("/") && !path.startsWith("//")) {
                    return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
                }
            } catch (IllegalArgumentException e) {
                // a malformed header - fall back to the listings board
            }
        }
        return "/ads";
    }
}
