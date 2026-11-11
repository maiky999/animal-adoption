package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.dto.AdSearchCriteria;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.specification.AdSpecifications;
import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@Transactional
class AdRepositoryTest {

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private AdRepository adRepository;

    @Autowired
    private EntityManager entityManager;

    private User user1;
    private User user2;
    private Category categoryDogs;
    private Category categoryCats;

    @BeforeEach
    void setUp() {
        user1 = User.builder()
                .email("jan@example.com")
                .passwordHash("secretHash123")
                .displayName("Jan Kowalski")
                .mobile("+48123456789")
                .role(RoleEnum.USER)
                .userType(UserTypeEnum.PRIVATE)
                .isEmailVerified(true)
                .build();
        entityManager.persist(user1);

        user2 = User.builder()
                .email("anna@example.com")
                .passwordHash("secretHash456")
                .displayName("Anna Nowak")
                .mobile("+48987654321")
                .role(RoleEnum.USER)
                .userType(UserTypeEnum.PRIVATE)
                .isEmailVerified(true)
                .build();
        entityManager.persist(user2);

        categoryDogs = new Category();
        categoryDogs.setName("Psy");
        entityManager.persist(categoryDogs);

        categoryCats = new Category();
        categoryCats.setName("Koty");
        entityManager.persist(categoryCats);

        entityManager.flush();
    }

    @Test
    @DisplayName("findAllByStatusOrderByCreatedAtDesc - should return only active listings sorted newest first")
    void shouldFindAllByStatusOrderByCreatedAtDesc() {
        Ad olderActiveAd = createAndPersistAd("Starsze ogłoszenie", categoryDogs, user1,
                AdStatusEnum.ACTIVE, LocalDateTime.now());
        Ad newerActiveAd = createAndPersistAd("Nowsze ogłoszenie", categoryDogs, user1,
                AdStatusEnum.ACTIVE, LocalDateTime.now());
        createAndPersistAd("Oczekujące ogłoszenie", categoryDogs, user1,
                AdStatusEnum.PENDING, LocalDateTime.now());

        entityManager.createNativeQuery("UPDATE ads SET created_at = ? WHERE id = ?")
                .setParameter(1, LocalDateTime.now().minusDays(2))
                .setParameter(2, olderActiveAd.getId())
                .executeUpdate();

        entityManager.createNativeQuery("UPDATE ads SET created_at = ? WHERE id = ?")
                .setParameter(1, LocalDateTime.now().minusDays(1))
                .setParameter(2, newerActiveAd.getId())
                .executeUpdate();

        entityManager.clear();

        List<Ad> result = adRepository.findAllByStatusOrderByCreatedAtDesc(AdStatusEnum.ACTIVE);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Nowsze ogłoszenie");
        assertThat(result.get(1).getTitle()).isEqualTo("Starsze ogłoszenie");
    }

    @Test
    @DisplayName("findAllByCategoryIdAndStatusOrderByCreatedAtDesc - should filter by category and status")
    void shouldFindAllByCategoryIdAndStatusOrderByCreatedAtDesc() {
        createAndPersistAd("Pies aktywny", categoryDogs, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        createAndPersistAd("Pies oczekujący", categoryDogs, user1, AdStatusEnum.PENDING, LocalDateTime.now());
        createAndPersistAd("Kot aktywny", categoryCats, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());

        List<Ad> result = adRepository.findAllByCategoryIdAndStatusOrderByCreatedAtDesc(
                categoryDogs.getId(), AdStatusEnum.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Pies aktywny");
        assertThat(result.get(0).getCategory().getName()).isEqualTo("Psy");
    }

    @Test
    @DisplayName("findAllByAuthorEmailOrderByCreatedAtDesc - should return the listings of the given author")
    void shouldFindAllByAuthorEmailOrderByCreatedAtDesc() {
        createAndPersistAd("Ogłoszenie Jana", categoryDogs, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        createAndPersistAd("Ogłoszenie Anny", categoryCats, user2, AdStatusEnum.ACTIVE, LocalDateTime.now());

        List<Ad> result = adRepository.findAllByAuthorEmailOrderByCreatedAtDesc("jan@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Ogłoszenie Jana");
        assertThat(result.get(0).getAuthor().getEmail()).isEqualTo("jan@example.com");
    }

    // --- Listings board search (AdSpecifications) - run against a real database, so the generated SQL is checked too ---

    @Test
    @DisplayName("Search - only active listings, filtered by animal information")
    void shouldSearchActiveAdsByAnimalDetails() {
        Ad youngVaccinated = createAndPersistAd("Szczeniak Burek", categoryDogs, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        youngVaccinated.setAge(AnimalAgeEnum.YOUNG);
        youngVaccinated.setVaccinated(true);
        Ad youngNotVaccinated = createAndPersistAd("Szczeniak Azor", categoryDogs, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        youngNotVaccinated.setAge(AnimalAgeEnum.YOUNG);
        Ad pendingYoung = createAndPersistAd("Szczeniak w weryfikacji", categoryDogs, user1, AdStatusEnum.PENDING, LocalDateTime.now());
        pendingYoung.setAge(AnimalAgeEnum.YOUNG);
        pendingYoung.setVaccinated(true);
        entityManager.flush();

        AdSearchCriteria criteria = new AdSearchCriteria();
        criteria.setAge(AnimalAgeEnum.YOUNG);
        criteria.setVaccinated(true);

        List<Ad> result = adRepository.findAll(AdSpecifications.activeMatching(criteria));

        assertThat(result).extracting(Ad::getTitle).containsExactly("Szczeniak Burek");
    }

    @Test
    @DisplayName("Search - title and location are matched case-insensitively, also with Polish letters")
    void shouldSearchByTitleAndLocationIgnoringCase() {
        createAndPersistAd("Żółw Leon", categoryCats, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        createAndPersistAd("Kot Filemon", categoryCats, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());

        AdSearchCriteria criteria = new AdSearchCriteria();
        criteria.setQ("  żÓŁw ");
        criteria.setLocation("śLĄ");

        List<Ad> result = adRepository.findAll(AdSpecifications.activeMatching(criteria));

        assertThat(result).extracting(Ad::getTitle).containsExactly("Żółw Leon");
    }

    @Test
    @DisplayName("Search - % and _ typed by the user are treated as normal characters, not wildcards")
    void shouldEscapeLikeWildcards() {
        createAndPersistAd("Pies na 100% przyjazny", categoryDogs, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        createAndPersistAd("Pies przyjazny", categoryDogs, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());

        AdSearchCriteria percent = new AdSearchCriteria();
        percent.setQ("100%");
        AdSearchCriteria underscore = new AdSearchCriteria();
        underscore.setQ("_");

        assertThat(adRepository.findAll(AdSpecifications.activeMatching(percent)))
                .extracting(Ad::getTitle).containsExactly("Pies na 100% przyjazny");
        assertThat(adRepository.findAll(AdSpecifications.activeMatching(underscore))).isEmpty();
    }

    @Test
    @DisplayName("Search - results are split into pages of the requested size")
    void shouldReturnPagedResults() {
        for (int i = 0; i < 5; i++) {
            createAndPersistAd("Kot numer " + i, categoryCats, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        }

        // Sorted by title, because @CreationTimestamp gives all five listings practically the same date
        Page<Ad> secondPage = adRepository.findAll(AdSpecifications.activeMatching(new AdSearchCriteria()),
                PageRequest.of(1, 2, Sort.by("title")));

        assertThat(secondPage.getTotalElements()).isEqualTo(5);
        assertThat(secondPage.getTotalPages()).isEqualTo(3);
        assertThat(secondPage.getContent()).extracting(Ad::getTitle).containsExactly("Kot numer 2", "Kot numer 3");
    }

    private Ad createAndPersistAd(String title, Category category, User author,
                                  AdStatusEnum status, LocalDateTime createdAt) {
        Ad ad = Ad.builder()
                .title(title)
                .description("Opis dla " + title)
                .location("Śląsk")
                .category(category)
                .author(author)
                .status(status)
                .createdAt(createdAt)
                .build();
        entityManager.persist(ad);
        entityManager.flush();
        return ad;
    }
}