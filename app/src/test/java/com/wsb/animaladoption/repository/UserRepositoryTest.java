package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@Transactional
class UserRepositoryTest {

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findByEmail - powinien znaleźć użytkownika po poprawnym adresie email")
    void shouldFindUserByEmail() {
        User user = User.builder()
                .email("test.repo@example.com")
                .passwordHash("hash123")
                .displayName("Repo Tester")
                .mobile("+48111222333")
                .role(RoleEnum.USER)
                .userType(UserTypeEnum.PRIVATE)
                .isEmailVerified(true)
                .build();
        entityManager.persist(user);
        entityManager.flush();

        Optional<User> found = userRepository.findByEmail("test.repo@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Repo Tester");
        assertThat(found.get().getEmail()).isEqualTo("test.repo@example.com");
    }

    @Test
    @DisplayName("findByEmail - powinien zwrócić pusty Optional, gdy użytkownik nie istnieje")
    void shouldReturnEmptyWhenUserNotFound() {
        Optional<User> found = userRepository.findByEmail("nieistniejacy@example.com");

        assertThat(found).isEmpty();
    }
}