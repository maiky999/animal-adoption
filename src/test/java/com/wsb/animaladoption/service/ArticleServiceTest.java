package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.ArticleCreateDto;
import com.wsb.animaladoption.model.Article;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.ArticleRepository;
import com.wsb.animaladoption.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ArticleService articleService;

    @Test
    @DisplayName("findAll - powinien zwrócić wszystkie artykuły")
    void shouldFindAllArticles() {
        Article a1 = Article.builder().id(1L).title("Artykuł 1").build();
        Article a2 = Article.builder().id(2L).title("Artykuł 2").build();
        when(articleRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a1, a2));

        List<Article> result = articleService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Artykuł 1");
    }

    @Test
    @DisplayName("findLatest - powinien ograniczyć liczbę zwracanych artykułów")
    void shouldLimitLatestArticles() {
        Article a1 = Article.builder().id(1L).title("Artykuł 1").build();
        Article a2 = Article.builder().id(2L).title("Artykuł 2").build();
        Article a3 = Article.builder().id(3L).title("Artykuł 3").build();
        when(articleRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a1, a2, a3));

        List<Article> result = articleService.findLatest(2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Artykuł 1");
        assertThat(result.get(1).getTitle()).isEqualTo("Artykuł 2");
    }

    @Test
    @DisplayName("findById - powinien zwrócić artykuł, gdy istnieje")
    void shouldFindArticleById() {
        Article article = Article.builder().id(10L).title("Jak dbać o psa").build();
        when(articleRepository.findById(10L)).thenReturn(Optional.of(article));

        Article result = articleService.findById(10L);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Jak dbać o psa");
    }

    @Test
    @DisplayName("findById - powinien rzucić IllegalArgumentException, gdy artykuł nie istnieje")
    void shouldThrowExceptionWhenArticleNotFound() {
        when(articleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie znaleziono artykułu");
    }

    @Test
    @DisplayName("createArticle - powinien zapisać plik, powiązać autora i utworzyć artykuł")
    void shouldCreateArticleSuccessfully() {
        User author = User.builder().id(1L).email("author@example.com").build();
        MockMultipartFile imageFile = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());

        ArticleCreateDto dto = new ArticleCreateDto();
        dto.setTitle("Poradnik adopcyjny");
        dto.setContent("Treść poradnika...");
        dto.setImageFile(imageFile);

        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(author));
        when(fileStorageService.storeFile(imageFile)).thenReturn("/uploads/test.jpg");
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Article created = articleService.createArticle(dto, "author@example.com");

        assertThat(created.getTitle()).isEqualTo("Poradnik adopcyjny");
        assertThat(created.getContent()).isEqualTo("Treść poradnika...");
        assertThat(created.getImageUrl()).isEqualTo("/uploads/test.jpg");
        assertThat(created.getAuthor()).isEqualTo(author);

        verify(fileStorageService, times(1)).storeFile(imageFile);
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    @Test
    @DisplayName("createArticle - powinien rzucić wyjątek gdy autor nie istnieje")
    void shouldThrowExceptionWhenAuthorNotFound() {
        ArticleCreateDto dto = new ArticleCreateDto();
        when(userRepository.findByEmail("nieistnieje@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.createArticle(dto, "nieistnieje@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie znaleziono użytkownika");

        verify(articleRepository, never()).save(any());
        verify(fileStorageService, never()).storeFile(any());
    }
}