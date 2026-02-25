package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    public String verifyEmail(@RequestParam("token") String token, RedirectAttributes redirectAttributes) {
        boolean isVerified = userService.verifyEmail(token);

        if (isVerified) {
            redirectAttributes.addFlashAttribute("successMessage", "Twój adres e-mail został pomyślnie zweryfikowany! Możesz się teraz zalogować.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Link weryfikacyjny jest nieprawidłowy lub wygasł.");
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
        } catch (IllegalArgumentException e) {
            result.rejectValue("email", "error.userRegistrationDto", e.getMessage());
            return "user/register";
        } catch (DataIntegrityViolationException e) {
            result.rejectValue("email", "error.userRegistrationDto", "Ten adres email jest już powiązany z innym kontem.");
            return "user/register";
        }

        return "redirect:/login?success";
    }
}
