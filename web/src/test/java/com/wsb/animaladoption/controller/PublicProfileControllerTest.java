package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AdService adService;

    @InjectMocks
    private PublicProfileController publicProfileController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(publicProfileController).build();
    }

    @Test
    @DisplayName("GET /users/me - should redirect the logged-in user to their own public profile")
    void shouldRedirectLoggedInUserToOwnProfile() throws Exception {
        User user = new User();
        user.setId(2L);
        when(userService.findByEmail("jan@example.com")).thenReturn(user);

        var authentication = new UsernamePasswordAuthenticationToken("jan@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/users/me").principal(authentication))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/2"));
    }

    @Test
    @DisplayName("GET /users/me - should send a guest to the login page")
    void shouldRedirectGuestToLogin() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(userService);
    }
}
