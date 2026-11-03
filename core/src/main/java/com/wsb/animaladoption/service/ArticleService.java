package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.ArticleCreateDto;
import com.wsb.animaladoption.model.Article;

import java.util.List;

/**
 * Business logic for articles published on the portal.
 */
public interface ArticleService {

    /** Returns all articles, newest first. */
    List<Article> findAll();

    /** Returns the given number of the newest articles. */
    List<Article> findLatest(int limit);

    /**
     * Returns the article with the given id.
     *
     * @throws IllegalArgumentException if the article does not exist
     */
    Article findById(Long id);

    /** Creates a new article authored by the given user. */
    Article createArticle(ArticleCreateDto dto, String authorEmail);
}
