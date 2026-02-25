package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepository;

    @GetMapping
    public String showProfile(@AuthenticationPrincipal UserDetails currentUser, Model model) {
        User user = userRepository.findByEmail(currentUser.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Brak użytkownika w bazie"));

        if (!model.containsAttribute("profileDto")) {
            UserProfileUpdateDto profileDto = new UserProfileUpdateDto();
            profileDto.setEmail(user.getEmail());
            profileDto.setDisplayName(user.getDisplayName());
            profileDto.setMobile(user.getMobile());
            model.addAttribute("profileDto", profileDto);
        }

        if (!model.containsAttribute("passwordDto")) {
            model.addAttribute("passwordDto", new UserPasswordUpdateDto());
        }

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
            String currentEmail = currentUser.getUsername();
            userService.updateUserProfile(currentEmail, dto);

            if (!currentEmail.equalsIgnoreCase(dto.getEmail())) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                Authentication newAuth = new UsernamePasswordAuthenticationToken(dto.getEmail(), auth.getCredentials(), auth.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }

            redirectAttributes.addFlashAttribute("successMessage", "Dane profilu zostały pomyślnie zaktualizowane.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("profileDto", dto);
        }

        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String updatePassword(@Valid @ModelAttribute("passwordDto") UserPasswordUpdateDto dto,
                                 BindingResult result,
                                 @AuthenticationPrincipal UserDetails currentUser,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.passwordDto", result);
            redirectAttributes.addFlashAttribute("passwordDto", dto);
            return "redirect:/profile";
        }

        try {
            userService.changeUserPassword(currentUser.getUsername(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "Hasło zostało pomyślnie zmienione.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorPasswordMessage", e.getMessage());
        }

        return "redirect:/profile";
    }
}
