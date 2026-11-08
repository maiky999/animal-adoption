package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Blocking user accounts - from the moderation panel on the user's public profile (/users/{id}).
 * Available to moderators and administrators (see SecurityConfig, /moderation/**).
 */
@Controller
@RequestMapping("/moderation/users")
@RequiredArgsConstructor
public class ModerationUserController {
    private final UserService userService;

    @PostMapping("/{id}/ban")
    public String banUser(@PathVariable Long id,
                          @RequestParam(name = "reason", required = false) String reason,
                          @RequestParam(name = "hours", required = false) Integer hours,
                          RedirectAttributes redirectAttributes) {
        if (!ModerationController.isValidReason(reason)) {
            redirectAttributes.addFlashAttribute("moderationError", "moderation.users.ban.reasonInvalid");
        } else if (hours == null || hours < 1 || hours > UserService.MAX_BAN_HOURS) {
            redirectAttributes.addFlashAttribute("moderationError", "moderation.users.ban.hoursInvalid");
        } else {
            try {
                userService.banUser(id, reason, hours);
                redirectAttributes.addFlashAttribute("moderationMessage", "moderation.users.ban.success");
            } catch (LocalizedException e) {
                // e.g. an attempt to block a moderator or an administrator
                redirectAttributes.addFlashAttribute("moderationError", e.getMessageKey());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        return "redirect:/users/" + id;
    }

    @PostMapping("/{id}/unban")
    public String unbanUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.unbanUser(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        redirectAttributes.addFlashAttribute("moderationMessage", "moderation.users.unban.success");
        return "redirect:/users/" + id;
    }
}
