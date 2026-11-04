package com.wsb.animaladoption.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Reading the key from environment variables / application.yml
    @Value("${app.security.remember-me-key}")
    private String rememberMeKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
    public SecurityFilterChain filterChain(HttpSecurity http, TokenBasedRememberMeServices rememberMeServices) throws Exception {
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
        return http.build();
    }

    public String[] getWhitelist() {
        return new String[] {
                "/", "/ads", "/ads/**", "/register",
                "/login", "/css/**", "/js/**", "/images/**",
                "/error", "/uploads/**", "/verify", "/users/**",
                "/articles", "/articles/**"
        };
    }
}