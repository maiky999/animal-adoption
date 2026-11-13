package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.config.SessionAuthenticationUpdater;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.dto.VerificationResult;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.enums.VerificationResultEnum;
import com.wsb.animaladoption.security.AltchaService;
import com.wsb.animaladoption.security.RegistrationBotGuard;
import com.wsb.animaladoption.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SessionAuthenticationUpdater sessionAuthenticationUpdater;

    @Mock
    private AltchaService altchaService;

    /** The real guard (not a mock) - the tests control it through the session attribute and the honeypot field. */
    @Spy
    private RegistrationBotGuard botGuard = new RegistrationBotGuard();

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        // By default the CAPTCHA is solved correctly - individual tests change this when needed
        lenient().when(altchaService.verify(any())).thenReturn(true);
    }

    /**
     * POST /register as sent by a human: the form was displayed 10 seconds earlier (time in the session),
     * the honeypot field is empty and the ALTCHA solution is attached.
     */
    private MockHttpServletRequestBuilder humanRegistration(String email, String password, String displayName,
                                                            String mobile, String userType) {
        return post("/register")
                .sessionAttr(RegistrationBotGuard.FORM_SHOWN_AT, Instant.now().minusSeconds(10))
                .param("email", email)
                .param("password", password)
                .param("confirmPassword", password)
                .param("displayName", displayName)
                .param("mobile", mobile)
                .param("userType", userType)
                .param("homepage", "")
                .param("altcha", "solved-payload");
    }

    private MockHttpServletRequestBuilder validHumanRegistration() {
        return humanRegistration("jan@example.com", "bezpieczneHaslo123", "Jan Kowalski", "123456789",
                UserTypeEnum.PRIVATE.name());
    }

    @Test
    @DisplayName("GET /login - should return the login view")
    void shouldReturnLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/login"));
    }

    @Test
    @DisplayName("GET /register - should return the registration view with an empty DTO in the model")
    void shouldReturnRegisterViewWithDto() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeExists("userRegistrationDto"))
                // The display time is saved, so that the fill-in time can be checked on submit
                .andExpect(request().sessionAttribute(RegistrationBotGuard.FORM_SHOWN_AT, notNullValue()));
    }

    @Test
    @DisplayName("GET /verify - should redirect to login with a success message")
    void shouldVerifyEmailSuccessfully() throws Exception {
        when(userService.verifyEmail("valid-token")).thenReturn(
                new VerificationResult(VerificationResultEnum.ACCOUNT_VERIFIED, "jan@example.com", "jan@example.com"));

        mockMvc.perform(get("/verify").param("token", "valid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService, times(1)).verifyEmail("valid-token");
    }

    @Test
    @DisplayName("GET /verify - should redirect to login with an error message when the token is invalid")
    void shouldFailEmailVerificationWhenTokenInvalid() throws Exception {
        when(userService.verifyEmail("invalid-token")).thenReturn(VerificationResult.invalid());

        mockMvc.perform(get("/verify").param("token", "invalid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(userService, times(1)).verifyEmail("invalid-token");
    }

    @Test
    @DisplayName("POST /register - should register the user and redirect to /login?success")
    void shouldRegisterUserSuccessfully() throws Exception {
        mockMvc.perform(humanRegistration("jan.kowalski@example.com", "bezpieczneHaslo123", "Jan Kowalski",
                        "123456789", UserTypeEnum.PRIVATE.name()))
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
    @DisplayName("POST /register - should return the form when the data fails validation")
    void shouldReturnRegisterViewWhenValidationFails() throws Exception {
        mockMvc.perform(humanRegistration("niepoprawny-email", "short", "", "123", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto",
                        "email", "password", "displayName", "mobile", "userType"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - should return the form when the repeated password does not match")
    void shouldRejectRegistrationWhenPasswordsDoNotMatch() throws Exception {
        mockMvc.perform(validHumanRegistration().param("confirmPassword", "inneHaslo123"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto", "passwordConfirmed"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - should handle IllegalArgumentException and reject the email field")
    void shouldHandleIllegalArgumentExceptionOnRegister() throws Exception {
        doThrow(new IllegalArgumentException("Użytkownik o podanym adresie email już istnieje."))
                .when(userService).registerNewUser(any(UserRegistrationDto.class));

        mockMvc.perform(validHumanRegistration())
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto", "email"));

        verify(userService, times(1)).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - should handle DataIntegrityViolationException on a database conflict")
    void shouldHandleDataIntegrityViolationExceptionOnRegister() throws Exception {
        doThrow(new DataIntegrityViolationException("Duplicate key"))
                .when(userService).registerNewUser(any(UserRegistrationDto.class));

        mockMvc.perform(validHumanRegistration())
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto", "email"));

        verify(userService, times(1)).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - should reject a disposable email address")
    void shouldRejectDisposableEmail() throws Exception {
        mockMvc.perform(humanRegistration("bot@mailinator.com", "bezpieczneHaslo123", "Bot", "123456789",
                        UserTypeEnum.PRIVATE.name()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasFieldErrors("userRegistrationDto", "email"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - a filled-in honeypot field should pretend success without creating an account")
    void shouldSilentlyIgnoreBotThatFilledHoneypot() throws Exception {
        mockMvc.perform(validHumanRegistration().param("homepage", "http://spam.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?success"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - a form sent without displaying it first (no time in the session) should be rejected")
    void shouldRejectFormSentWithoutDisplayingIt() throws Exception {
        mockMvc.perform(post("/register")
                        .param("email", "jan@example.com")
                        .param("password", "bezpieczneHaslo123")
                        .param("displayName", "Jan Kowalski")
                        .param("mobile", "123456789")
                        .param("userType", UserTypeEnum.PRIVATE.name())
                        .param("altcha", "solved-payload"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasErrors("userRegistrationDto"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - a form sent right after displaying it should be rejected")
    void shouldRejectFormSentTooFast() throws Exception {
        mockMvc.perform(validHumanRegistration()
                        .sessionAttr(RegistrationBotGuard.FORM_SHOWN_AT, Instant.now()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasErrors("userRegistrationDto"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    @Test
    @DisplayName("POST /register - should reject the form when the ALTCHA solution is wrong or missing")
    void shouldRejectFormWithoutValidCaptcha() throws Exception {
        when(altchaService.verify(any())).thenReturn(false);

        mockMvc.perform(validHumanRegistration())
                .andExpect(status().isOk())
                .andExpect(view().name("user/register"))
                .andExpect(model().attributeHasErrors("userRegistrationDto"));

        verify(userService, never()).registerNewUser(any(UserRegistrationDto.class));
    }

    // --- Forgot password ---

    @Test
    @DisplayName("POST /forgot-password - always the same message, whether the account exists or not")
    void shouldShowSameMessageAfterPasswordResetRequest() throws Exception {
        mockMvc.perform(post("/forgot-password").param("email", "ktokolwiek@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage", "passwordReset.requested"));

        verify(userService).requestPasswordReset("ktokolwiek@example.com");
    }

    @Test
    @DisplayName("GET /reset-password - a valid link shows the form with the token in a hidden field")
    void shouldShowResetFormForValidToken() throws Exception {
        when(userService.isPasswordResetTokenValid("abc")).thenReturn(true);

        mockMvc.perform(get("/reset-password").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/reset-password"))
                .andExpect(model().attribute("passwordResetDto", hasProperty("token", is("abc"))));
    }

    @Test
    @DisplayName("GET /reset-password - an invalid link redirects to login with an error")
    void shouldRedirectForInvalidResetToken() throws Exception {
        when(userService.isPasswordResetTokenValid("zly")).thenReturn(false);

        mockMvc.perform(get("/reset-password").param("token", "zly"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("errorMessage", "passwordReset.invalid"));
    }

    @Test
    @DisplayName("POST /reset-password - sets the new password and redirects to login")
    void shouldResetPassword() throws Exception {
        when(userService.resetPassword("abc", "noweHaslo123")).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .param("token", "abc")
                        .param("newPassword", "noweHaslo123")
                        .param("confirmPassword", "noweHaslo123"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage", "passwordReset.success"));
    }

    @Test
    @DisplayName("POST /reset-password - different passwords in both fields show the form again")
    void shouldRejectMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .param("token", "abc")
                        .param("newPassword", "noweHaslo123")
                        .param("confirmPassword", "inneHaslo123"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/reset-password"))
                .andExpect(model().attributeHasFieldErrors("passwordResetDto", "passwordConfirmed"));

        verify(userService, never()).resetPassword(any(), any());
    }

    @Test
    @DisplayName("POST /reset-password - a link that expired in the meantime redirects to login with an error")
    void shouldHandleTokenExpiredWhileFormWasOpen() throws Exception {
        when(userService.resetPassword("abc", "noweHaslo123")).thenReturn(false);

        mockMvc.perform(post("/reset-password")
                        .param("token", "abc")
                        .param("newPassword", "noweHaslo123")
                        .param("confirmPassword", "noweHaslo123"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("errorMessage", "passwordReset.invalid"));
    }
}
