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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

        verify(adService, times(1)).approveAd(10L);
    }

    @Test
    @DisplayName("POST /moderation/ads/{id}/reject - should reject the listing with the reason and redirect to the list")
    void shouldRejectAdAndRedirect() throws Exception {
        mockMvc.perform(post("/moderation/ads/10/reject").param("reason", "Zdjęcia nie przedstawiają zwierzęcia"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderation/ads"));

        verify(adService, times(1)).rejectAd(10L, "Zdjęcia nie przedstawiają zwierzęcia");
    }

    @Test
    @DisplayName("POST /moderation/ads/{id}/reject - without a reason the listing is not rejected")
    void shouldNotRejectWithoutReason() throws Exception {
        mockMvc.perform(post("/moderation/ads/10/reject").param("reason", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderation/ads"))
                .andExpect(flash().attribute("errorMessage", "moderation.ads.reject.reasonInvalid"));

        verify(adService, never()).rejectAd(anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /moderation/ads/{id}/remove - removes the listing and goes back to the page it was removed from")
    void shouldRemoveAdAndGoBack() throws Exception {
        mockMvc.perform(post("/moderation/ads/10/remove")
                        .param("reason", "Sprzedaż zwierzęcia")
                        .header("Referer", "http://localhost:8080/ads?q=burek&page=2"))
                .andExpect(redirectedUrl("/ads?q=burek&page=2"))
                .andExpect(flash().attribute("moderationMessage", "moderation.ads.remove.success"));

        verify(adService).removeAd(10L, "Sprzedaż zwierzęcia");
    }

    @Test
    @DisplayName("POST /moderation/ads/{id}/remove - without a reason nothing is removed")
    void shouldNotRemoveWithoutReason() throws Exception {
        mockMvc.perform(post("/moderation/ads/10/remove").param("reason", ""))
                .andExpect(redirectedUrl("/ads"))
                .andExpect(flash().attribute("moderationError", "moderation.ads.remove.reasonInvalid"));

        verify(adService, never()).removeAd(anyLong(), anyString());
    }

    @Test
    @DisplayName("previousPage - only the path of the Referer is used, so a forged header cannot lead to another site")
    void shouldNeverRedirectToAnotherHost() {
        assertThat(ModerationController.previousPage(withReferer("https://evil.example.com/phishing"))).isEqualTo("/phishing");
        assertThat(ModerationController.previousPage(withReferer("//evil.example.com"))).isEqualTo("/ads");
        assertThat(ModerationController.previousPage(withReferer(null))).isEqualTo("/ads");
        assertThat(ModerationController.previousPage(withReferer("not a uri ::"))).isEqualTo("/ads");
    }

    private static MockHttpServletRequest withReferer(String referer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (referer != null) {
            request.addHeader("Referer", referer);
        }
        return request;
    }

    @Test
    @DisplayName("POST /moderation/ads/{id}/reject - a reason longer than the database column is not accepted")
    void shouldNotRejectWithTooLongReason() throws Exception {
        mockMvc.perform(post("/moderation/ads/10/reject").param("reason", "x".repeat(501)))
                .andExpect(flash().attribute("errorMessage", "moderation.ads.reject.reasonInvalid"));

        verify(adService, never()).rejectAd(anyLong(), anyString());
    }
}