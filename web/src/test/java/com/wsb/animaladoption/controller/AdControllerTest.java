package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdSearchCriteria;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.AdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdService adService;

    @Mock
    private UserDetails mockUser;

    @InjectMocks
    private AdController adController;

    @BeforeEach
    void setUp() {
        // Resolver that ensures mockUser is correctly injected into @AuthenticationPrincipal
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return mockUser;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(adController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("GET /ads - should return the list of listings and the ads/list view")
    void shouldReturnAdsListPage() throws Exception {
        when(adService.searchActiveAds(any(AdSearchCriteria.class), eq(0))).thenReturn(Page.empty());
        when(adService.findAllCategories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/ads"))
                .andExpect(status().isOk())
                .andExpect(view().name("ads/list"))
                .andExpect(model().attributeExists("ads", "adsPage", "categories", "criteria"))
                .andExpect(model().attributeDoesNotExist("previousPageUrl", "nextPageUrl"));

        verify(adService, times(1)).searchActiveAds(any(AdSearchCriteria.class), eq(0));
    }

    @Test
    @DisplayName("GET /ads?... - URL parameters become search criteria, page links keep the filters")
    void shouldPassFiltersFromUrlToSearch() throws Exception {
        // Page 2 (index 1) of 3 - both "previous" and "next" links should exist
        Page<Ad> page = new PageImpl<>(List.of(), PageRequest.of(1, AdService.PAGE_SIZE), AdService.PAGE_SIZE * 3);
        when(adService.searchActiveAds(any(AdSearchCriteria.class), eq(1))).thenReturn(page);

        // Parameters in the URL itself (not .param()), just like a GET form sends them - the page links are built from it
        mockMvc.perform(get("/ads?q=burek&age=YOUNG&vaccinated=true&sort=OLDEST&page=1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("previousPageUrl", "/ads?q=burek&age=YOUNG&vaccinated=true&sort=OLDEST&page=0"))
                .andExpect(model().attribute("nextPageUrl", "/ads?q=burek&age=YOUNG&vaccinated=true&sort=OLDEST&page=2"));

        ArgumentCaptor<AdSearchCriteria> captor = ArgumentCaptor.forClass(AdSearchCriteria.class);
        verify(adService).searchActiveAds(captor.capture(), eq(1));
        AdSearchCriteria criteria = captor.getValue();
        assertEquals("burek", criteria.getQ());
        assertEquals(AnimalAgeEnum.YOUNG, criteria.getAge());
        assertEquals(Boolean.TRUE, criteria.getVaccinated());
        assertNull(criteria.getSterilized());
        assertEquals(AdSearchCriteria.Sort.OLDEST, criteria.getSort());
    }

    @Test
    @DisplayName("GET /ads/{id} - should return the modal fragment with listing details")
    void shouldReturnAdDetailsModalFragment() throws Exception {
        Ad ad = Ad.builder()
                .id(1L)
                .title("Kot Mruczek")
                .description("Spokojny kot")
                .location("Katowice")
                .status(AdStatusEnum.ACTIVE)
                .build();

        when(adService.findById(1L)).thenReturn(ad);
        when(adService.canView(eq(ad), any(), anyBoolean())).thenReturn(true);

        mockMvc.perform(get("/ads/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("ads/fragments/ad-modal :: content"))
                .andExpect(model().attribute("ad", ad));

        verify(adService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /ads/{id} - should return 404 when the viewer may not see the listing (e.g. pending)")
    void shouldReturnNotFoundForHiddenAd() throws Exception {
        Ad ad = Ad.builder().id(2L).status(AdStatusEnum.PENDING).build();

        when(adService.findById(2L)).thenReturn(ad);
        when(adService.canView(eq(ad), any(), anyBoolean())).thenReturn(false);

        mockMvc.perform(get("/ads/2"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /ads/new - should display the add listing form")
    void shouldShowCreateForm() throws Exception {
        when(adService.findAllCategories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/ads/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("ads/form"))
                .andExpect(model().attributeExists("adCreateDto"))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    @DisplayName("POST /ads/new - should create a listing and redirect to /my-ads")
    void shouldCreateAdAndRedirect() throws Exception {
        when(mockUser.getUsername()).thenReturn("autor@example.com");

        mockMvc.perform(post("/ads/new")
                        .param("title", "Pies Burek szuka domu")
                        .param("description", "Opis pieska do adopcji")
                        .param("location", "Katowice")
                        .param("categoryId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-ads"))
                .andExpect(flash().attributeExists("infoMessage"));

        verify(adService, times(1)).createAd(any(AdCreateDto.class), eq("autor@example.com"));
    }

    @Test
    @DisplayName("GET /ads/{id}/edit - should display the edit form when the user is the author")
    void shouldShowEditFormWhenUserIsAuthor() throws Exception {
        when(mockUser.getUsername()).thenReturn("autor@example.com");

        User author = User.builder().email("autor@example.com").build();
        Category category = new Category();
        category.setId(2L);

        Ad ad = Ad.builder()
                .id(5L)
                .title("Tytuł")
                .description("Opis")
                .location("Gliwice")
                .author(author)
                .category(category)
                .build();

        when(adService.findById(5L)).thenReturn(ad);
        when(adService.findAllCategories()).thenReturn(List.of(category));

        mockMvc.perform(get("/ads/5/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("ads/form"))
                .andExpect(model().attribute("editMode", true))
                .andExpect(model().attribute("adId", 5L));
    }

    @Test
    @DisplayName("GET /ads/{id}/edit - should redirect to /my-ads when the logged-in user is not the author")
    void shouldRedirectWhenUserIsNotAuthor() throws Exception {
        when(mockUser.getUsername()).thenReturn("obcy@example.com");

        User author = User.builder().email("prawdziwy_autor@example.com").build();
        Ad ad = Ad.builder().id(5L).author(author).build();

        when(adService.findById(5L)).thenReturn(ad);

        mockMvc.perform(get("/ads/5/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-ads"));
    }

    @Test
    @DisplayName("POST /ads/{id}/adoption-form - should process the adoption questionnaire")
    void shouldProcessAdoptionFormSuccessfully() throws Exception {
        doNothing().when(adService).processAdoptionForm(eq(1L), any(AdoptionFormDto.class));

        mockMvc.perform(post("/ads/1/adoption-form")
                        .param("firstName", "Anna")
                        .param("lastName", "Kowalska")
                        .param("email", "anna@example.com")
                        .param("phone", "123456789")
                        .param("city", "Katowice")
                        .param("address", "Ulica 1")
                        .param("birthYear", "1998")
                        .param("consent", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("ads/fragments/ad-modal :: form-response"))
                .andExpect(model().attributeExists("formSuccess"));

        verify(adService, times(1)).processAdoptionForm(eq(1L), any(AdoptionFormDto.class));
    }
}