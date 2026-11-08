package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdSearchCriteria;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.enums.AnimalSexEnum;
import com.wsb.animaladoption.enums.AnimalSizeEnum;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.service.AdService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import com.wsb.animaladoption.security.StaffRoles;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/ads")
@RequiredArgsConstructor
public class AdController {
    private final AdService adService;

    /** Options of the animal selects - added to every view of this controller (board filters and the listing form). */
    @ModelAttribute
    public void addAnimalOptions(Model model) {
        model.addAttribute("ageOptions", AnimalAgeEnum.values());
        model.addAttribute("sexOptions", AnimalSexEnum.values());
        model.addAttribute("sizeOptions", AnimalSizeEnum.values());
    }

    /**
     * Listings board with filters and pagination. The filters come from URL parameters, so the browser's
     * "back" button, bookmarks and shared links keep the search. The filter form sends the request through HTMX
     * and swaps only the results (see ads/list.html) - without HTMX it still works as a regular form.
     */
    @GetMapping
    public String getAdsPage(@ModelAttribute("criteria") AdSearchCriteria criteria,
                             @RequestParam(defaultValue = "0") int page,
                             HttpServletRequest request,
                             Model model) {
        Page<Ad> result = adService.searchActiveAds(criteria, page);

        model.addAttribute("ads", result.getContent());
        model.addAttribute("adsPage", result);
        model.addAttribute("categories", adService.findAllCategories());
        if (result.hasPrevious()) {
            model.addAttribute("previousPageUrl", pageUrl(request, result.getNumber() - 1));
        }
        if (result.hasNext()) {
            model.addAttribute("nextPageUrl", pageUrl(request, result.getNumber() + 1));
        }
        return "ads/list";
    }

    /** The current URL with all filters kept and only the page number changed. */
    private static String pageUrl(HttpServletRequest request, int page) {
        return UriComponentsBuilder.fromPath(request.getRequestURI())
                .query(request.getQueryString())
                .replaceQueryParam("page", page)
                .build()
                .toUriString();
    }

    @GetMapping("/{id}")
    public String getAdDetailsHtml(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails currentUser,
                                   Model model) {
        Ad ad = adService.findById(id);

        String viewerEmail = currentUser != null ? currentUser.getUsername() : null;
        boolean viewerIsModerator = isModerator(currentUser);
        if (!adService.canView(ad, viewerEmail, viewerIsModerator)) {
            // 404 instead of 403, so a stranger cannot even tell that a hidden listing with this id exists
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("ad", ad);
        // Moderators and administrators see the "remove listing" form in the window
        model.addAttribute("viewerIsModerator", viewerIsModerator);
        return "ads/fragments/ad-modal :: content";
    }

    private static boolean isModerator(UserDetails user) {
        return user != null && StaffRoles.isStaff(user.getAuthorities());
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

        // Someone else's listing, or one removed by a moderator (it cannot be edited any more)
        if (!ad.getAuthor().getEmail().equals(currentUser.getUsername()) || ad.getStatus() == AdStatusEnum.REMOVED) {
            return "redirect:/my-ads";
        }

        model.addAttribute("adCreateDto", AdCreateDto.from(ad));
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