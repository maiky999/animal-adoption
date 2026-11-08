package com.wsb.animaladoption.security;

import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Moderation through the whole application (security rules, controllers, services, database):
 * who may use it, blocking an account and what a blocked user experiences.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@AutoConfigureMockMvc
@Transactional
class ModerationAccessAndBanTest {

    private static final String USER_EMAIL = "uzytkownik@example.com";
    private static final String PASSWORD = "bezpieczneHaslo123";

    @MockitoBean
    private ConnectionFactory connectionFactory;

    /** Emails (ad removal) are not sent in tests. */
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdRepository adRepository;

    private User user;
    private Ad activeAd;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email(USER_EMAIL).passwordHash(passwordEncoder.encode(PASSWORD))
                .displayName("Jan").mobile("123456789")
                .role(RoleEnum.USER).userType(UserTypeEnum.PRIVATE).isEmailVerified(true)
                .build();
        entityManager.persist(user);
        // Staff accounts must exist in the database - otherwise StaleSessionInterceptor logs the session out
        persistStaff("moderator@example.com", RoleEnum.MODERATOR);
        persistStaff("admin@example.com", RoleEnum.ADMIN);
        Category category = new Category(null, "Psy");
        entityManager.persist(category);
        activeAd = Ad.builder().title("Burek szuka domu").description("Opis ogłoszenia testowego.")
                .location("Kraków").category(category).author(user).status(AdStatusEnum.ACTIVE).build();
        entityManager.persist(activeAd);
        entityManager.persist(Ad.builder().title("Kot czeka na weryfikację").description("Opis ogłoszenia testowego.")
                .location("Kraków").category(category).author(user).status(AdStatusEnum.PENDING).build());
        entityManager.flush();
    }

    // --- Access: ADMIN has everything MODERATOR has ---

    @ParameterizedTest(name = "{0} sees the moderation panel")
    @ValueSource(strings = {"MODERATOR", "ADMIN"})
    void staffCanOpenModerationPanel(String role) throws Exception {
        String html = mockMvc.perform(get("/moderation/ads").with(staff(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // the "!" badge in the navigation: one listing waits for approval
        assertThat(html).contains("Ogłoszenia czekające na akceptację: 1");
    }

    @ParameterizedTest(name = "{0} can block an account")
    @ValueSource(strings = {"MODERATOR", "ADMIN"})
    void staffCanBanUser(String role) throws Exception {
        mockMvc.perform(post("/moderation/users/{id}/ban", user.getId())
                        .param("reason", "Spam").param("hours", "24")
                        .with(staff(role)).with(csrf()))
                .andExpect(redirectedUrl("/users/" + user.getId()));

        entityManager.flush();
        entityManager.refresh(user);
        assertThat(user.isBanned()).isTrue();

        // Both listings of the blocked user (the active and the pending one) are removed with the ban reason
        List<Ad> ads = adRepository.findAllByAuthorEmailOrderByCreatedAtDesc(USER_EMAIL);
        assertThat(ads).hasSize(2).allSatisfy(ad -> {
            entityManager.refresh(ad);
            assertThat(ad.getStatus()).isEqualTo(AdStatusEnum.REMOVED);
            assertThat(ad.getRemovalReason()).isEqualTo("Konto autora zostało zablokowane. Powód: Spam");
        });
        // ...and the moderation queue is empty again
        assertThat(adRepository.countByStatus(AdStatusEnum.PENDING)).isZero();
    }

    @ParameterizedTest(name = "{0} can remove a listing")
    @ValueSource(strings = {"MODERATOR", "ADMIN"})
    void staffCanRemoveAd(String role) throws Exception {
        mockMvc.perform(post("/moderation/ads/{id}/remove", activeAd.getId())
                        .param("reason", "Sprzedaż zwierzęcia")
                        .with(staff(role)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        entityManager.flush();
        entityManager.refresh(activeAd);
        assertThat(activeAd.getStatus()).isEqualTo(AdStatusEnum.REMOVED);
        assertThat(activeAd.getRemovalReason()).isEqualTo("Sprzedaż zwierzęcia");
    }

    @Test
    @DisplayName("An ordinary user has no access to moderation and does not see the panel on profiles")
    void ordinaryUserHasNoModerationAccess() throws Exception {
        mockMvc.perform(post("/moderation/users/{id}/ban", user.getId())
                        .param("reason", "x").param("hours", "1")
                        .with(user("inny@example.com").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/moderation/pending-badge").with(user("inny@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        String profile = mockMvc.perform(get("/users/{id}", user.getId()).with(user("inny@example.com").roles("USER")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(profile).doesNotContain("Moderacja konta");
    }

    // --- What a blocked user experiences ---

    @Test
    @DisplayName("A blocked user cannot log in even with the correct password and sees the reason and end of the ban")
    void bannedUserSeesReasonWhenLoggingIn() throws Exception {
        ban(user, "Wielokrotny spam");

        MvcResult login = mockMvc.perform(formLogin("/login").user(USER_EMAIL).password(PASSWORD))
                .andExpect(redirectedUrl("/login?banned"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();
        String page = mockMvc.perform(get("/login").param("banned", "").session(session))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("Konto zablokowane", "Wielokrotny spam");
    }

    @Test
    @DisplayName("A user blocked while logged in is logged out on the next request")
    void loggedInUserIsLoggedOutAfterBan() throws Exception {
        ban(user, "Spam");

        mockMvc.perform(get("/my-ads").with(user(USER_EMAIL).roles("USER")))
                .andExpect(redirectedUrl("/login?banned"));
    }

    @Test
    @DisplayName("After the ban ends, the user can log in again")
    void userCanLogInAfterBanExpires() throws Exception {
        user.setBannedUntil(LocalDateTime.now().minusMinutes(1));
        user.setBanReason("Stara blokada");
        entityManager.flush();

        mockMvc.perform(formLogin("/login").user(USER_EMAIL).password(PASSWORD))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("A moderator account cannot be blocked")
    void moderatorCannotBeBanned() throws Exception {
        User moderator = User.builder().email("mod@example.com").passwordHash("hash").displayName("Mod")
                .mobile("123456789").role(RoleEnum.MODERATOR).userType(UserTypeEnum.PRIVATE).isEmailVerified(true).build();
        entityManager.persist(moderator);

        mockMvc.perform(post("/moderation/users/{id}/ban", moderator.getId())
                        .param("reason", "Spam").param("hours", "24")
                        .with(staff("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/users/" + moderator.getId()));

        entityManager.flush();
        entityManager.refresh(moderator);
        assertThat(moderator.isBanned()).isFalse();
    }

    private void ban(User target, String reason) {
        target.setBannedUntil(LocalDateTime.now().plusHours(24));
        target.setBanReason(reason);
        entityManager.flush();
    }

    private void persistStaff(String email, RoleEnum role) {
        entityManager.persist(User.builder().email(email).passwordHash("hash").displayName(role.name())
                .mobile("123456789").role(role).userType(UserTypeEnum.PRIVATE).isEmailVerified(true).build());
    }

    /** A logged-in moderator or administrator (an account that exists in the database). */
    private static RequestPostProcessor staff(String role) {
        return user(role.toLowerCase() + "@example.com").roles(role);
    }
}
