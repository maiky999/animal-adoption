package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JpaSpecificationExecutor adds findAll(Specification, Pageable) - used by the listings board search,
 * where the query is assembled from the chosen filters (see AdSpecifications in the core module).
 */
@Repository
public interface AdRepository extends JpaRepository<Ad, Long>, JpaSpecificationExecutor<Ad> {
    List<Ad> findAllByStatusOrderByCreatedAtDesc(AdStatusEnum adStatusEnum);

    /** All listings of the author except those with the given status (e.g. not removed yet - when blocking the account). */
    List<Ad> findAllByAuthorIdAndStatusNot(Long authorId, AdStatusEnum status);

    /** Number of listings with the status - e.g. how many wait for moderation (badge in the navigation). */
    long countByStatus(AdStatusEnum status);
    List<Ad> findAllByCategoryIdAndStatusOrderByCreatedAtDesc(Long categoryId, AdStatusEnum adStatusEnum);
    List<Ad> findAllByAuthorEmailOrderByCreatedAtDesc(String email);

    List<Ad> findAllByAuthorIdAndStatusOrderByCreatedAtDesc(Long authorId, AdStatusEnum statusEnum);
}
