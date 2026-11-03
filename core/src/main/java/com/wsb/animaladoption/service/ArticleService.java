package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.ArticleCreateDto;
import com.wsb.animaladoption.model.Article;

import java.util.List;

/**
 * Logika biznesowa związana z artykułami publikowanymi w serwisie.
 */
public interface ArticleService {

    /** Zwraca wszystkie artykuły, od najnowszego. */
    List<Article> findAll();

    /** Zwraca określoną liczbę najnowszych artykułów. */
    List<Article> findLatest(int limit);

    /**
     * Zwraca artykuł o podanym id.
     *
     * @throws IllegalArgumentException gdy artykuł nie istnieje
     */
    Article findById(Long id);

    /** Tworzy nowy artykuł, którego autorem jest wskazany użytkownik. */
    Article createArticle(ArticleCreateDto dto, String authorEmail);
}
