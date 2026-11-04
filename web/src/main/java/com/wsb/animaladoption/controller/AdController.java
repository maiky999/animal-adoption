package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.service.AdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/ads")
@RequiredArgsConstructor
public class AdController {
    private final AdService adService;

    @GetMapping
    public String getAdsPage(@RequestParam(required = false) Long categoryId, Model model) {
        List<Ad> ads = adService.findActiveAds(categoryId);
        model.addAttribute("ads", ads);
        model.addAttribute("categories", adService.findAllCategories());
        return "ads/list";
    }

    @GetMapping("/{id}")
    public String getAdDetailsHtml(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails currentUser,
                                   Model model) {
        Ad ad = adService.findById(id);

        String viewerEmail = currentUser != null ? currentUser.getUsername() : null;
        if (!adService.canView(ad, viewerEmail, isModerator(currentUser))) {
            // 404 instead of 403, so a stranger cannot even tell that a hidden listing with this id exists
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("ad", ad);
        return "ads/fragments/ad-modal :: content";
    }

    private static boolean isModerator(UserDetails user) {
        return user != null && user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_MODERATOR") || role.equals("ROLE_ADMIN"));
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("adCreateDto", new AdCreateDto());
        model.addAttribute("categories", adService.findAllCategories());
        return "ads/form";
    }

    @PostMapping("/new")
    public String createAd(@Valid @ModelAttribute AdCreateDto adDto,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal UserDetails currentUser,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", adService.findAllCategories());
            return "ads/form";
        }
        try {
            adService.createAd(adDto, currentUser.getUsername());
        } catch (LocalizedException e) {
            // e.g. an uploaded file is not an image
            model.addAttribute("errorMessage", e.getMessageKey());
            model.addAttribute("categories", adService.findAllCategories());
            return "ads/form";
        }
        redirectAttributes.addFlashAttribute("infoMessage", "ads.created.pending");
        return "redirect:/my-ads";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, @AuthenticationPrincipal UserDetails currentUser) {
        Ad ad = adService.findById(id);

        if (!ad.getAuthor().getEmail().equals(currentUser.getUsername())) {
            return "redirect:/my-ads";
        }

        AdCreateDto dto = new AdCreateDto();
        dto.setTitle(ad.getTitle());
        dto.setDescription(ad.getDescription());
        dto.setCategoryId(ad.getCategory().getId());
        dto.setLocation(ad.getLocation());

        model.addAttribute("adCreateDto", dto);
        model.addAttribute("categories", adService.findAllCategories());

        model.addAttribute("editMode", true);
        model.addAttribute("adId", id);

        return "ads/form";
    }

    @PostMapping("/{id}/edit")
    public String updateAd(@PathVariable Long id,
                           @Valid @ModelAttribute("adCreateDto") AdCreateDto dto,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal UserDetails currentUser,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", adService.findAllCategories());
            model.addAttribute("editMode", true);
            model.addAttribute("adId", id);
            return "ads/form";
        }

        try {
            adService.updateAd(id, dto, currentUser.getUsername());
        } catch (LocalizedException e) {
            model.addAttribute("errorMessage", e.getMessageKey());
            model.addAttribute("categories", adService.findAllCategories());
            model.addAttribute("editMode", true);
            model.addAttribute("adId", id);
            return "ads/form";
        }
        redirectAttributes.addFlashAttribute("infoMessage", "ads.updated.pending");
        return "redirect:/my-ads";
    }

    @PostMapping("/{id}/adoption-form")
    public String submitAdoptionForm(@PathVariable Long id,
                                     @Valid @ModelAttribute AdoptionFormDto dto,
                                     BindingResult bindingResult,
                                     Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("formError", "adoption.form.invalid");
            return "ads/fragments/ad-modal :: form-response";
        }

        try {
            adService.processAdoptionForm(id, dto);
            model.addAttribute("formSuccess", "adoption.form.sent");
        } catch (LocalizedException e) {
            model.addAttribute("formError", e.getMessageKey());
        } catch (Exception e) {
            model.addAttribute("formError", "adoption.form.error");
        }

        return "ads/fragments/ad-modal :: form-response";
    }
}