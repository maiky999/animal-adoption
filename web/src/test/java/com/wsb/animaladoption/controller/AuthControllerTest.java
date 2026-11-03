package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("GET /login - powinien zwrócić widok logowania")
    void shouldReturnLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/login"));
    }

    @Test
    @DisplayName("GET /register - powinien zwrócić widok rejestracji z pustym DTO w modelu")
    void shouldReturnRegisterViewWithDto() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeExists("userRegistrationDto"));
    }

    @Test
    @DisplayName("GET /verify - powinien przekierować do logowania z komunikatem sukcesu")
    void shouldVerifyEmailSuccessfully() throws Exception {
        when(userService.verifyEmail("valid-token")).thenReturn(true);

        mockMvc.perform(get("/verify").param("token", "valid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService, times(1)).verifyEmail("valid-token");
    }

    @Test
    @DisplayName("GET /verify - powinien przekierować do logowania z komunikatem błędu, gdy token nieprawidłowy")
    void shouldFailEmailVerificationWhenTokenInvalid() throws Exception {
        when(userService.verifyEmail("invalid-token")).thenReturn(false);

        mockMvc.perform(get("/verify").param("token", "invalid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(userService, times(1)).verifyEmail("invalid-token");
    }

    @Test
    @DisplayName("POST /register - powinien zarejestrować użytkownika i przekierować do /login?success")
    void shouldRegisterUserSuccessfully() throws Exception {
        mockMvc.perform(post("/register")
                        .param("email", "jan.kowalski@example.com")
                        .param("password", "bezpieczneHaslo123")
                        .param("displayName", "Jan Kowalski")
                        .param("mobile", "123456789")
                        .param("userType", UserTypeEnum.PRIVATE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?success"));

        verify(userService, times(1)).registerNewUser(argThat(dto ->
                dto.getEmail().equals("jan.kowalski@example.com") &&
                        dto.getDisplayName().equals("Jan Kowalski") &&
                        dto.getMobile().equals("123456789") &&
                        dto.getUserType() == UserTypeEnum.PRIVATE
        ));
    }

    @Test
    @DisplayName("POST /register - powinien zwrócić formularz, gdy dane nie przejdą walidacji")
    void shouldReturnRegisterViewWhenValidationFails() throws Exception {
        mockMvc.perform(post("/register")
                        .param("email", "niepoprawny-email")
                        .param("password", "short")
                        .param("displayName", "")
                        .param("mobile", "123")
                        .param("userType", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto",
                        "email", "password", "displayName", "mobile", "userType"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - powinien obsłużyć IllegalArgumentException i odrzucić pole email")
    void shouldHandleIllegalArgumentExceptionOnRegister() throws Exception {
        doThrow(new IllegalArgumentException("Użytkownik o podanym adresie email już istnieje."))
                .when(userService).registerNewUser(any(UserRegistrationDto.class));

        mockMvc.perform(post("/register")
                        .param("email", "jan@example.com")
                        .param("password", "bezpieczneHaslo123")
                        .param("displayName", "Jan Kowalski")
                        .param("mobile", "123456789")
                        .param("userType", UserTypeEnum.PRIVATE.name()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto", "email"));

        verify(userService, times(1)).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - powinien obsłużyć DataIntegrityViolationException przy kolizji bazy")
    void shouldHandleDataIntegrityViolationExceptionOnRegister() throws Exception {
        doThrow(new DataIntegrityViolationException("Duplicate key"))
                .when(userService).registerNewUser(any(UserRegistrationDto.class));

        mockMvc.perform(post("/register")
                        .param("email", "jan@example.com")
                        .param("password", "bezpieczneHaslo123")
                        .param("displayName", "Jan Kowalski")
                        .param("mobile", "123456789")
                        .param("userType", UserTypeEnum.PRIVATE.name()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto", "email"));

        verify(userService, times(1)).registerNewUser(any(UserRegistrationDto.class));
    }
}