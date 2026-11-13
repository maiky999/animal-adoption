package com.wsb.animaladoption.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Sends one request through the filter and returns the response status (200 = reached the controller). */
    private int send(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("POST /register - the 6th registration from the same IP within an hour should get 429")
    void shouldLimitRegistrationsPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", "/register", "203.0.113.7")).isEqualTo(200);
        }
        assertThat(send("POST", "/register", "203.0.113.7")).isEqualTo(429);

        // Another IP address is not affected
        assertThat(send("POST", "/register", "198.51.100.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /register (displaying the form) should not be limited")
    void shouldNotLimitOtherRequests() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(send("GET", "/register", "203.0.113.7")).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("The 429 response should tell the client when to try again (Retry-After)")
    void shouldSetRetryAfterHeader() throws Exception {
        for (int i = 0; i < 10; i++) {
            send("POST", "/login", "203.0.113.7");
        }
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("Logged-in users should be counted per account, not per (shared) IP address")
    void shouldCountLoggedInUsersPerAccount() throws Exception {
        String sharedIp = "10.0.0.1";
        loginAs("anna@example.com");
        for (int i = 0; i < 20; i++) {
            assertThat(send("POST", "/messages/1/2", sharedIp)).isEqualTo(200);
        }
        assertThat(send("POST", "/messages/1/2", sharedIp)).isEqualTo(429);

        // Another user behind the same IP (e.g. the same university network) can still send messages
        loginAs("piotr@example.com");
        assertThat(send("POST", "/messages/1/2", sharedIp)).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /users/{id}/phone - revealing phone numbers en masse (a bot collecting them) should get 429")
    void shouldLimitPhoneNumberReveals() throws Exception {
        // Different users - the limit counts all revealed numbers of one client, not one number
        for (int i = 1; i <= 30; i++) {
            assertThat(send("GET", "/users/" + i + "/phone", "203.0.113.7")).isEqualTo(200);
        }
        assertThat(send("GET", "/users/31/phone", "203.0.113.7")).isEqualTo(429);

        // The profile page itself is not limited
        assertThat(send("GET", "/users/31", "203.0.113.7")).isEqualTo(200);
    }

    private static void loginAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }
}
