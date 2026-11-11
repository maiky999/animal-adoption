package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Role management - from the administrator panel on the user's public profile (/users/{id}).
 * Only for administrators (see SecurityConfig, /admin/**). This is how new moderators and administrators
 * are appointed; the very first administrator is created at startup (InitialAdminCreator).
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam("role") RoleEnum role,
                             @AuthenticationPrincipal UserDetails admin,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.changeUserRole(id, role, admin.getUsername());
            redirectAttributes.addFlashAttribute("moderationMessage", "moderation.users.role.success");
        } catch (LocalizedException e) {
            // e.g. an attempt to change one's own role
            redirectAttributes.addFlashAttribute("moderationError", e.getMessageKey());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:/users/" + id;
    }
}
