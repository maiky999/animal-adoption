package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.config.SessionAuthenticationUpdater;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.dto.VerificationResult;
import com.wsb.animaladoption.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final SessionAuthenticationUpdater sessionAuthenticationUpdater;

    @GetMapping("/login")
    public String showLoginForm() {
        return "user/login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("userRegistrationDto", new UserRegistrationDto());
        return "user/register";
    }

    @GetMapping("/verify")
    public String verifyEmail(@RequestParam("token") String token,
                              Authentication authentication,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              RedirectAttributes redirectAttributes) {
        VerificationResult result = userService.verifyEmail(token);

        switch (result.status()) {
            case ACCOUNT_VERIFIED -> redirectAttributes.addFlashAttribute("successMessage", "verify.success");
            case EMAIL_TAKEN -> redirectAttributes.addFlashAttribute("errorMessage", "verify.emailTaken");
            case INVALID -> redirectAttributes.addFlashAttribute("errorMessage", "verify.invalid");
            case EMAIL_CHANGED -> {
                redirectAttributes.addFlashAttribute("successMessage", "verify.emailChanged");
                // If the link was opened in the browser where the user is logged in, we switch their session
                // to the new email right away, so they can continue without logging in again
                if (authentication != null && authentication.getName().equalsIgnoreCase(result.previousEmail())) {
                    sessionAuthenticationUpdater.refresh(result.currentEmail(), request, response);
                    return "redirect:/profile";
                }
            }
        }

        return "redirect:/login";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("userRegistrationDto") UserRegistrationDto dto, BindingResult result) {
        if (result.hasErrors()) {
            return "user/register";
        }
        try {
            dto.setEmail(dto.getEmail().trim().toLowerCase());
            userService.registerNewUser(dto);
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            // The error code is a translation key - Spring resolves it through MessageSource when displaying the field
            result.rejectValue("email", "validation.email.taken");
            return "user/register";
        }

        return "redirect:/login?success";
    }
}
