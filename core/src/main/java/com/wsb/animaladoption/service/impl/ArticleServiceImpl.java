package com.wsb.animaladoption.service.impl;

import com.wsb.animaladoption.dto.ArticleCreateDto;
import com.wsb.animaladoption.model.Article;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.ArticleRepository;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.service.ArticleService;
import com.wsb.animaladoption.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Override
    public List<Article> findAll() {
        return articleRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Article> findLatest(int limit) {
        return articleRepository.findAllByOrderByCreatedAtDesc().stream()
                .limit(limit)
                .toList();
    }

    @Override
    public Article findById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono artykułu"));
    }

    @Override
    @Transactional
    public Article createArticle(ArticleCreateDto dto, String authorEmail) {
        User author = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));

        String imageUrl = fileStorageService.storeFile(dto.getImageFile());

        Article article = Article.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .imageUrl(imageUrl)
                .author(author)
                .build();

        return articleRepository.save(article);
    }
}
