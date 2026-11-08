package com.wsb.animaladoption.i18n;

import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Article;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.Message;
import com.wsb.animaladoption.model.PasswordResetToken;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.service.impl.UserServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test of the views: renders every page of the application in both languages and checks that
 * <ul>
 *     <li>the template renders without errors (e.g. a typo in a Thymeleaf expression),</li>
 *     <li>there are no missing translations (Thymeleaf displays them as {@code ??key_locale??}),</li>
 *     <li>the page is actually in the selected language.</li>
 * </ul>
 * English is selected with the {@code ?lang=en} parameter - just like with the switcher in the navigation.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@AutoConfigureMockMvc
@Transactional
class PageRenderingI18nTest {

    private static final String AUTHOR_EMAIL = "fundacja@example.com";
    private static final String RESET_TOKEN = "test-reset-token";

    /** HTMX fragments - rendered without the page layout. */
    private static final Set<String> FRAGMENTS = Set.of("/ads/{adId}", "/messages/{adId}/{otherUserId}/new?after=0",
            "/moderation/pending-badge");

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private Long adId;
    private Long authorId;
    private Long otherUserId;
    private Long articleId;

    @BeforeEach
    void setUpData() {
        Category category = new Category(null, "Gady i płazy");
        entityManager.persist(category);

        User author = persistUser(AUTHOR_EMAIL, "Fundacja Azyl", UserTypeEnum.ORGANIZATION, RoleEnum.MODERATOR);
        User other = persistUser("jan@example.com", "Jan Kowalski", UserTypeEnum.PRIVATE, RoleEnum.USER);

        Ad ad = persistAd("Żółw szuka spokojnego domu", category, author, AdStatusEnum.ACTIVE);
        ad.setAge(AnimalAgeEnum.SENIOR);
        ad.setVaccinated(true);
        Ad pending = persistAd("Gekon czeka na weryfikację", category, author, AdStatusEnum.PENDING);
        // Corrected after an earlier rejection - the moderator sees the previous reason
        pending.setRejectionReason("Brak zdjęć");

        // A valid "set a new password" link (the database keeps only the hash of the token)
        entityManager.persist(new PasswordResetToken(UserServiceImpl.hashToken(RESET_TOKEN), other));

        Article article = Article.builder()
                .title("Jak dbać o żółwia")
                .content("Treść poradnika o żółwiach, wystarczająco długa, aby przejść walidację.")
                .author(author)
                .build();
        entityManager.persist(article);

        entityManager.persist(Message.builder()
                .sender(author).receiver(other).relatedAd(ad)
                .content("Dzień dobry!").isRead(false)
                .build());
        entityManager.flush();

        adId = ad.getId();
        authorId = author.getId();
        otherUserId = other.getId();
        articleId = article.getId();
    }

    /** Page, whether it requires login, expected text in Polish and in English. */
    static Stream<Arguments> pages() {
        return Stream.of(
                Arguments.of("/", false, "Daj im nowy", "Give them a new"),
                Arguments.of("/", false, "Gady i płazy", "Reptiles and amphibians"),
                Arguments.of("/ads", false, "Tablica ogłoszeń", "Listings board"),
                Arguments.of("/ads/{adId}", false, "Wypełnij ankietę przedadopcyjną", "Fill in the pre-adoption survey"),
                Arguments.of("/ads/{adId}", false, "Zaszczepiony", "Vaccinated"),
                // Filters: case-insensitive search with Polish letters, location fragment, age and a feature
                Arguments.of("/ads?q=ŻÓŁW&location=krak&age=SENIOR&vaccinated=true", false,
                        "Żółw szuka spokojnego domu", "Żółw szuka spokojnego domu"),
                Arguments.of("/ads?age=YOUNG", false, "Brak ogłoszeń spełniających kryteria", "No listings match your criteria"),
                Arguments.of("/forgot-password", false, "Nie pamiętasz hasła?", "Forgot your password?"),
                Arguments.of("/reset-password?token=" + RESET_TOKEN, false, "Ustaw nowe hasło", "Set a new password"),
                Arguments.of("/messages/{adId}/{otherUserId}/new?after=0", true, "Dzień dobry!", "Dzień dobry!"),
                Arguments.of("/articles", false, "Poradniki i Edukacja", "Guides and Education"),
                Arguments.of("/articles/{articleId}", false, "Napisane przez eksperta", "Written by an expert"),
                Arguments.of("/login", false, "Witaj ponownie", "Welcome back"),
                Arguments.of("/register", false, "Dołącz do społeczności Łapki!", "Join the Łapka community!"),
                Arguments.of("/users/{authorId}", false, "Zaufany opiekun", "Trusted caretaker"),
                Arguments.of("/ads/new", true, "Dodaj nowe ogłoszenie", "Add a new listing"),
                Arguments.of("/ads/{adId}/edit", true, "Zapisz zmiany", "Save changes"),
                Arguments.of("/my-ads", true, "Weryfikacja", "Under review"),
                Arguments.of("/profile", true, "Ustawienia profilu", "Profile settings"),
                Arguments.of("/messages", true, "Ty: Dzień dobry!", "You: Dzień dobry!"),
                Arguments.of("/messages/{adId}/{otherUserId}", true, "Dotyczy ogłoszenia:", "Regarding listing:"),
                Arguments.of("/moderation/ads", true, "Panel Moderatora", "Moderator Panel"),
                Arguments.of("/moderation/ads", true, "Wcześniej odrzucone z powodu:", "Previously rejected because:"),
                Arguments.of("/moderation/pending-badge", true,
                        "Ogłoszenia czekające na akceptację: 1", "Listings waiting for approval: 1"),
                Arguments.of("/ads/{adId}", true, "Usuń ogłoszenie (moderacja)", "Remove listing (moderation)"),
                Arguments.of("/users/{otherUserId}", true, "Moderacja konta", "Account moderation"),
                // The author's own profile: a moderator account cannot be blocked
                Arguments.of("/users/{authorId}", true,
                        "Konta moderatorów i administratorów nie mogą", "Moderator and administrator accounts cannot"),
                Arguments.of("/moderation/articles/new", true, "Panel Edukatora", "Educator Panel")
        );
    }

    @ParameterizedTest(name = "{0} -> PL: \"{2}\"")
    @MethodSource("pages")
    void shouldRenderPageInPolish(String path, boolean authenticated, String polishText, String englishText) throws Exception {
        String html = render(get(resolve(path)), authenticated);

        assertThat(html).contains(polishText).doesNotContain("??");
        if (isFullPage(path)) {
            assertThat(html).contains("lang=\"pl\"", "Stworzone z");
        }
    }

    @ParameterizedTest(name = "{0} -> EN: \"{3}\"")
    @MethodSource("pages")
    void shouldRenderPageInEnglish(String path, boolean authenticated, String polishText, String englishText) throws Exception {
        String resolved = resolve(path);
        String html = render(get(resolved + (resolved.contains("?") ? "&" : "?") + "lang=en"), authenticated);

        assertThat(html).contains(englishText).doesNotContain("??");
        if (isFullPage(path)) {
            // the whole page (including the layout) must be in English, not just a fragment of it
            assertThat(html).contains("lang=\"en\"", "Made with").doesNotContain("Stworzone z");
        }
    }

    /** HTMX fragments (e.g. the listing details window) have no layout - the other pages have the full layout. */
    private static boolean isFullPage(String path) {
        return !FRAGMENTS.contains(path);
    }

    private String render(MockHttpServletRequestBuilder request, boolean authenticated) throws Exception {
        if (authenticated) {
            request.with(user(AUTHOR_EMAIL).roles("MODERATOR"));
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String resolve(String path) {
        return path.replace("{adId}", adId.toString())
                .replace("{authorId}", authorId.toString())
                .replace("{otherUserId}", otherUserId.toString())
                .replace("{articleId}", articleId.toString());
    }

    private User persistUser(String email, String name, UserTypeEnum type, RoleEnum role) {
        User user = User.builder()
                .email(email)
                .passwordHash("hash")
                .displayName(name)
                .mobile("123456789")
                .role(role)
                .userType(type)
                .isEmailVerified(true)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Ad persistAd(String title, Category category, User author, AdStatusEnum status) {
        Ad ad = Ad.builder()
                .title(title)
                .description("Spokojny zwierzak szuka odpowiedzialnego opiekuna.")
                .location("Kraków")
                .category(category)
                .author(author)
                .status(status)
                .build();
        entityManager.persist(ad);
        return ad;
    }
}
