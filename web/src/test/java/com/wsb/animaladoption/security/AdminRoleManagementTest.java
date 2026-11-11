package com.wsb.animaladoption.security;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giving roles through the whole application (security rules, controller, service, database, session refresh).
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminRoleManagementTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String MODERATOR_EMAIL = "moderator@example.com";
    private static final String USER_EMAIL = "uzytkownik@example.com";

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private User admin;
    private User user;

    @BeforeEach
    void setUp() {
        admin = persist(ADMIN_EMAIL, RoleEnum.ADMIN);
        persist(MODERATOR_EMAIL, RoleEnum.MODERATOR);
        user = persist(USER_EMAIL, RoleEnum.USER);
        entityManager.flush();
    }

    @Test
    @DisplayName("An administrator appoints a moderator from the user's profile")
    void adminCanChangeRole() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/role", user.getId()).param("role", "MODERATOR")
                        .with(loggedIn(ADMIN_EMAIL, "ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/users/" + user.getId()))
                .andExpect(flash().attribute("moderationMessage", "moderation.users.role.success"));

        entityManager.flush();
        entityManager.refresh(user);
        assertThat(user.getRole()).isEqualTo(RoleEnum.MODERATOR);
    }

    @Test
    @DisplayName("A moderator and an ordinary user cannot give roles")
    void onlyAdminCanChangeRole() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/role", user.getId()).param("role", "ADMIN")
                        .with(loggedIn(MODERATOR_EMAIL, "MODERATOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/{id}/role", user.getId()).param("role", "ADMIN")
                        .with(loggedIn(USER_EMAIL, "USER")).with(csrf()))
                .andExpect(status().isForbidden());

        entityManager.refresh(user);
        assertThat(user.getRole()).isEqualTo(RoleEnum.USER);
    }

    @Test
    @DisplayName("An administrator cannot change their own role")
    void adminCannotChangeOwnRole() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/role", admin.getId()).param("role", "USER")
                        .with(loggedIn(ADMIN_EMAIL, "ADMIN")).with(csrf()))
                .andExpect(flash().attribute("moderationError", "moderation.users.role.self"));

        entityManager.refresh(admin);
        assertThat(admin.getRole()).isEqualTo(RoleEnum.ADMIN);
    }

    @Test
    @DisplayName("An unknown role is rejected")
    void unknownRoleIsRejected() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/role", user.getId()).param("role", "ROOT")
                        .with(loggedIn(ADMIN_EMAIL, "ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Only an administrator sees the role panel, and not on their own profile")
    void rolePanelVisibility() throws Exception {
        assertThat(profile(user, loggedIn(ADMIN_EMAIL, "ADMIN"))).contains("Rola w serwisie");
        assertThat(profile(user, loggedIn(MODERATOR_EMAIL, "MODERATOR"))).doesNotContain("Rola w serwisie");
        assertThat(profile(admin, loggedIn(ADMIN_EMAIL, "ADMIN"))).doesNotContain("Rola w serwisie");
    }

    @Test
    @DisplayName("A logged-in user gets the new role on the next request, without logging in again")
    void sessionIsRefreshedAfterRoleChange() throws Exception {
        // The session was created when the user was still an ordinary user...
        user.setRole(RoleEnum.MODERATOR);
        entityManager.flush();

        // ...so the request is repeated with the refreshed session
        // (the query string in the URL itself - MockMvc's .param() does not show up in getQueryString())
        MvcResult result = mockMvc.perform(get("/my-ads?x=1").with(loggedIn(USER_EMAIL, "USER")))
                .andExpect(redirectedUrl("/my-ads?x=1"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_MODERATOR");

        // and with that session the moderation panel is available right away
        mockMvc.perform(get("/moderation/ads").session(session)).andExpect(status().isOk());
    }

    private String profile(User owner, RequestPostProcessor viewer) throws Exception {
        return mockMvc.perform(get("/users/{id}", owner.getId()).with(viewer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private User persist(String email, RoleEnum role) {
        User u = User.builder().email(email).passwordHash("hash").displayName(role.name())
                .mobile("123456789").role(role).userType(UserTypeEnum.PRIVATE).isEmailVerified(true).build();
        entityManager.persist(u);
        return u;
    }

    private static RequestPostProcessor loggedIn(String email, String role) {
        return user(email).roles(role);
    }
}
