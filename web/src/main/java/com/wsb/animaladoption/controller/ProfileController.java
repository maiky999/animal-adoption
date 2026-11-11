package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.config.SessionAuthenticationUpdater;
import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final UserService userService;
    private final SessionAuthenticationUpdater sessionAuthenticationUpdater;

    @GetMapping
    public String showProfile(@AuthenticationPrincipal UserDetails currentUser, Model model) {
        User user = userService.findByEmail(currentUser.getUsername());

        if (!model.containsAttribute("profileDto")) {
            UserProfileUpdateDto profileDto = new UserProfileUpdateDto();
            profileDto.setEmail(user.getEmail());
            profileDto.setDisplayName(user.getDisplayName());
            profileDto.setMobile(user.getMobile());
            profileDto.setDescription(user.getDescription());
            model.addAttribute("profileDto", profileDto);
        }

        if (!model.containsAttribute("passwordDto")) {
            model.addAttribute("passwordDto", new UserPasswordUpdateDto());
        }

        // New email waiting for confirmation (shown under the email field)
        model.addAttribute("pendingEmail", user.getPendingEmail());

        return "user/profile";
    }

    @PostMapping("/info")
    public String updateProfileInfo(@Valid @ModelAttribute("profileDto") UserProfileUpdateDto dto,
                                    BindingResult result,
                                    @AuthenticationPrincipal UserDetails currentUser,
                                    RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.profileDto", result);
            redirectAttributes.addFlashAttribute("profileDto", dto);
            return "redirect:/profile";
        }

        try {
            // The email (login) does not change here - the user has to confirm the new address first,
            // so the session stays valid. It is refreshed in AuthController after the link is clicked.
            boolean emailChangePending = userService.updateUserProfile(currentUser.getUsername(), dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    emailChangePending ? "profile.email.pending" : "profile.updated");
        } catch (LocalizedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessageKey());
            redirectAttributes.addFlashAttribute("profileDto", dto);
        }

        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String updatePassword(@Valid @ModelAttribute("passwordDto") UserPasswordUpdateDto dto,
                                 BindingResult result,
                                 @AuthenticationPrincipal UserDetails currentUser,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.passwordDto", result);
            redirectAttributes.addFlashAttribute("passwordDto", dto);
            return "redirect:/profile";
        }

        try {
            userService.changeUserPassword(currentUser.getUsername(), dto);
            // The "remember me" cookie is signed with the password hash - after the change it would stop working,
            // so we issue a new one (other devices with the old cookie are logged out, which is desired)
            sessionAuthenticationUpdater.refresh(currentUser.getUsername(), request, response);
            redirectAttributes.addFlashAttribute("successMessage", "profile.password.changed");
        } catch (LocalizedException e) {
            redirectAttributes.addFlashAttribute("errorPasswordMessage", e.getMessageKey());
        }

        return "redirect:/profile";
    }
}
