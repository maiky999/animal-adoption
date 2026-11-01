package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.model.Article;
import com.wsb.animaladoption.service.ArticleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ArticleControllerTest {

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private ArticleController articleController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(articleController).build();
    }

    @Test
    @DisplayName("GET /articles - powinien zwrócić widok listy artykułów z modelem")
    void shouldReturnArticleListView() throws Exception {
        when(articleService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/articles"))
                .andExpect(status().isOk())
                .andExpect(view().name("articles/list"))
                .andExpect(model().attributeExists("articles"));

        verify(articleService, times(1)).findAll();
    }

    @Test
    @DisplayName("GET /articles/{id} - powinien zwrócić widok szczegółów artykułu")
    void shouldReturnArticleView() throws Exception {
        Article article = Article.builder().id(1L).title("Zasady adopcji").build();
        when(articleService.findById(1L)).thenReturn(article);

        mockMvc.perform(get("/articles/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("articles/view"))
                .andExpect(model().attribute("article", article));

        verify(articleService, times(1)).findById(1L);
    }
}