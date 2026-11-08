package com.wsb.animaladoption.config;

import com.wsb.animaladoption.security.RateLimitFilter;
import com.wsb.animaladoption.security.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Reading the key from environment variables / application.yml
    @Value("${app.security.remember-me-key}")
    private String rememberMeKey;

    /** Request limits (RateLimitFilter) - can be turned off e.g. for load tests. */
    @Value("${app.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Publishes login events (success, wrong password, locked account...) as Spring events.
     * LoginAttemptListener listens to them to count failed attempts. Defined explicitly,
     * so that it is clear where the events come from.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }

    /**
     * "Remember me" as a separate bean (instead of configuring it only inside the filter chain),
     * so that it can also be used outside of login - e.g. to issue a new cookie after an email change,
     * because the cookie contains the email (login) and would stop working.
     */
    @Bean
    public TokenBasedRememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(rememberMeKey, userDetailsService);
        services.setParameter("remember-me");
        services.setTokenValiditySeconds(7 * 24 * 60 * 60);
        return services;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenBasedRememberMeServices rememberMeServices,
                                           RateLimiter rateLimiter) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/moderation/**").hasAnyRole("MODERATOR", "ADMIN")
                        // Adding and editing listings requires login, even though the rest of /ads/** is public
                        .requestMatchers("/ads/new", "/ads/*/edit").authenticated()
                        .requestMatchers(getWhitelist()).permitAll()
                        .anyRequest().authenticated()
                ).formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                ).logout(logout -> logout
                        .logoutSuccessUrl("/?logout")
                        .permitAll()
                ).rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .rememberMeServices(rememberMeServices)
                );

        if (rateLimitEnabled) {
            // Before the login filter, so that POST /login is limited too. The filter is created here with "new"
            // (not as a @Bean/@Component), otherwise Spring Boot would also register it a second time
            // as a regular servlet filter, outside the security chain.
            http.addFilterBefore(new RateLimitFilter(rateLimiter), UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    public String[] getWhitelist() {
        return new String[] {
                "/", "/ads", "/ads/**", "/register",
                "/login", "/css/**", "/js/**", "/images/**",
                "/error", "/uploads/**", "/verify", "/users/**",
                "/articles", "/articles/**", "/altcha/**",
                "/forgot-password", "/reset-password"
        };
    }
}
