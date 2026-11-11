package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.security.StaffRoles;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {
    private final MessageService messageService;
    private final AdService adService;

    @ModelAttribute("messageService")
    public MessageService populateMessageService() {
        return messageService;
    }

    /**
     * Number of listings waiting for moderation - shown as a "!" badge next to "Moderation" in the navigation.
     * Counted only for moderators and administrators (others get null), so ordinary pages do not pay for the query.
     */
    @ModelAttribute("pendingAdsCount")
    public Long populatePendingAdsCount(Authentication authentication) {
        return StaffRoles.isStaff(authentication) ? adService.countPendingAds() : null;
    }
}
