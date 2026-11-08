package com.wsb.animaladoption.security;

import com.wsb.animaladoption.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Decides where a failed login goes. A blocked account (AccountExpiredException, see CustomUserDetailsService)
 * goes to the login page with the reason and the end of the ban; every other failure behaves as before
 * ("/login?error" with the exception remembered in the session).
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final UserService userService;

    public LoginFailureHandler(UserService userService) {
        super("/login?error");
        this.userService = userService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof AccountExpiredException) {
            // "username" is the name of the email field in the login form
            var bannedUser = userService.findActiveBan(request.getParameter("username"));
            if (bannedUser.isPresent()) {
                BanNotice.store(request, bannedUser.get());
                getRedirectStrategy().sendRedirect(request, response, BanNotice.LOGIN_URL);
                return;
            }
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
