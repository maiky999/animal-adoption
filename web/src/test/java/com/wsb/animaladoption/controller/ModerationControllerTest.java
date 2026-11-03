package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.service.AdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ModerationControllerTest {

    @Mock
    private AdService adService;

    @InjectMocks
    private ModerationController moderationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(moderationController).build();
    }

    @Test
    @DisplayName("GET /moderation/ads - should return the moderation/list view with pending listings")
    void shouldShowPendingAds() throws Exception {
        Ad pendingAd = Ad.builder().id(1L).title("Oczekujący pies").build();
        when(adService.findPendingAds()).thenReturn(List.of(pendingAd));

        mockMvc.perform(get("/moderation/ads"))
                .andExpect(status().isOk())
                .andExpect(view().name("moderation/list"))
                .andExpect(model().attribute("pendingAds", List.of(pendingAd)));

        verify(adService, times(1)).findPendingAds();
    }

    @Test
    @DisplayName("POST /moderation/ads/{id}/approve - should approve the listing and redirect to the list")
    void shouldApproveAdAndRedirect() throws Exception {
        mockMvc.perform(post("/moderation/ads/10/approve"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderation/ads"));

        verify(adService, times(1)).moderateAd(10L, true);
    }

    @Test
    @DisplayName("POST /moderation/ads/{id}/reject - should reject the listing and redirect to the list")
    void shouldRejectAdAndRedirect() throws Exception {
        mockMvc.perform(post("/moderation/ads/10/reject"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderation/ads"));

        verify(adService, times(1)).moderateAd(10L, false);
    }
}