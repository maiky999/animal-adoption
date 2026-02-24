package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long> {
    List<Ad> findAllByStatusOrderByCreatedAtDesc(AdStatusEnum adStatusEnum);
    List<Ad> findAllByCategoryIdAndStatusOrderByCreatedAtDesc(Long categoryId, AdStatusEnum adStatusEnum);
    List<Ad> findAllByAuthorEmailOrderByCreatedAtDesc(String email);
}
