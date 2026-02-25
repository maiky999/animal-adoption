package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.repository.CategoryRepository;
import com.wsb.animaladoption.service.AdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/ads")
@RequiredArgsConstructor
public class AdController {
    private final AdService adService;
    private final CategoryRepository categoryRepository;
    @GetMapping
    public String getAdsPage(@RequestParam(required = false) Long categoryId, Model model) {
        List<Ad> ads = adService.findActiveAds(categoryId);
        model.addAttribute("ads", ads);
        model.addAttribute("categories", categoryRepository.findAll());
        return "ads/list";
    }

    @GetMapping("/{id}")
    public String getAdDetailsHtml(@PathVariable Long id, Model model) {
        Ad ad = adService.findById(id);
        model.addAttribute("ad", ad);
        return "ads/fragments/ad-modal :: content"; // Zwraca tylko fragment HTML (Thymeleaf fragments)
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("adCreateDto", new AdCreateDto());
        model.addAttribute("categories", categoryRepository.findAllByOrderByIdAsc());
        return "ads/form";
    }

    @PostMapping("/new")
    public String createAd(@Valid @ModelAttribute AdCreateDto adDto,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal UserDetails currentUser,
                           Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findAllByOrderByIdAsc());
            return "ads/form";
        }
        adService.createAd(adDto, currentUser.getUsername());
        return "redirect:/ads";
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
        model.addAttribute("categories", categoryRepository.findAllByOrderByIdAsc());

        model.addAttribute("editMode", true);
        model.addAttribute("adId", id);

        return "ads/form";
    }

    @PostMapping("/{id}/edit")
    public String updateAd(@PathVariable Long id,
                           @Valid @ModelAttribute("adCreateDto") AdCreateDto dto,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal UserDetails currentUser,
                           Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findAllByOrderByIdAsc());
            model.addAttribute("editMode", true);
            model.addAttribute("adId", id);
            return "ads/form";
        }

        adService.updateAd(id, dto, currentUser.getUsername());
        return "redirect:/my-ads";
    }
}
