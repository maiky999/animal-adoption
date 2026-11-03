package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.enums.AdStatusEnum;
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
    @DisplayName("findAllByStatusOrderByCreatedAtDesc - powinien zwrócić tylko aktywne ogłoszenia posortowane od najnowszego")
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
    @DisplayName("findAllByCategoryIdAndStatusOrderByCreatedAtDesc - powinien filtrować po kategorii i statusie")
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
    @DisplayName("findAllByAuthorEmailOrderByCreatedAtDesc - powinien zwrócić ogłoszenia danego autora")
    void shouldFindAllByAuthorEmailOrderByCreatedAtDesc() {
        createAndPersistAd("Ogłoszenie Jana", categoryDogs, user1, AdStatusEnum.ACTIVE, LocalDateTime.now());
        createAndPersistAd("Ogłoszenie Anny", categoryCats, user2, AdStatusEnum.ACTIVE, LocalDateTime.now());

        List<Ad> result = adRepository.findAllByAuthorEmailOrderByCreatedAtDesc("jan@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Ogłoszenie Jana");
        assertThat(result.get(0).getAuthor().getEmail()).isEqualTo("jan@example.com");
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