package com.wsb.animaladoption.i18n;

import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Article;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.Message;
import com.wsb.animaladoption.model.User;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test "dymny" (smoke test) widoków: renderuje każdą stronę aplikacji w obu językach i sprawdza, że
 * <ul>
 *     <li>szablon renderuje się bez błędu (np. literówki w wyrażeniu Thymeleaf),</li>
 *     <li>nie ma brakujących tłumaczeń (Thymeleaf wyświetla je jako {@code ??klucz_jezyk??}),</li>
 *     <li>strona jest faktycznie w wybranym języku.</li>
 * </ul>
 * Język angielski wybierany jest parametrem {@code ?lang=en} - tak jak przełącznikiem w nawigacji.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@AutoConfigureMockMvc
@Transactional
class PageRenderingI18nTest {

    private static final String AUTHOR_EMAIL = "fundacja@example.com";

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
        persistAd("Gekon czeka na weryfikację", category, author, AdStatusEnum.PENDING);

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

    /** Strona, czy wymaga zalogowania, oczekiwany tekst po polsku i po angielsku. */
    static Stream<Arguments> pages() {
        return Stream.of(
                Arguments.of("/", false, "Daj im nowy", "Give them a new"),
                Arguments.of("/", false, "Gady i płazy", "Reptiles and amphibians"),
                Arguments.of("/ads", false, "Tablica ogłoszeń", "Listings board"),
                Arguments.of("/ads/{adId}", false, "Wypełnij ankietę przedadopcyjną", "Fill in the pre-adoption survey"),
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
        String html = render(get(resolve(path)).param("lang", "en"), authenticated);

        assertThat(html).contains(englishText).doesNotContain("??");
        if (isFullPage(path)) {
            // cała strona (łącznie z layoutem) ma być po angielsku, a nie tylko jej fragment
            assertThat(html).contains("lang=\"en\"", "Made with").doesNotContain("Stworzone z");
        }
    }

    /** Okno szczegółów ogłoszenia (/ads/{id}) to fragment HTMX bez layoutu - pozostałe strony mają pełny layout. */
    private static boolean isFullPage(String path) {
        return !path.equals("/ads/{adId}");
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
