package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.CategoryRepository;
import com.wsb.animaladoption.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdService {
    private final AdRepository adRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public List<Ad> findActiveAds(Long categoryId) {
        if (categoryId != null) {
            return adRepository.findAllByCategoryIdAndStatusOrderByCreatedAtDesc(categoryId, AdStatusEnum.ACTIVE);
        }
        return adRepository.findAllByStatusOrderByCreatedAtDesc(AdStatusEnum.ACTIVE);
    }

    public Ad findById(Long id) {
        return adRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono ogłoszenia"));
    }

    public List<Ad> findUserAds(String authorEmail) {
        return adRepository.findAllByAuthorEmailOrderByCreatedAtDesc(authorEmail);
    }

    @Transactional
    public void changeAdStatus(Long adId, AdStatusEnum newStatus, String authorEmail) {
        Ad ad = findById(adId);

        if (!ad.getAuthor().getEmail().equals(authorEmail)) {
            throw new SecurityException("Brak uprawnień do edycji tego ogłoszenia");
        }

        ad.setStatus(newStatus);
        adRepository.save(ad);
    }

    @Transactional
    public Ad createAd(AdCreateDto dto, String authorEmail) {
        User author = userRepository.findByEmail(authorEmail).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));
        Category category = categoryRepository.findById(dto.getCategoryId()).orElseThrow(() -> new IllegalArgumentException("Kategoria nie istnieje"));

        List<String> imageUrls = fileStorageService.storeFiles(dto.getImageFiles());

        Ad ad = Ad.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .imageUrls(imageUrls)
                .category(category)
                .author(author)
                .status(AdStatusEnum.ACTIVE)
                .build();

        return adRepository.save(ad);
    }

    @Transactional
    public void updateAd(Long id, AdCreateDto dto, String authorEmail) {
        Ad ad = findById(id);

        if (!ad.getAuthor().getEmail().equals(authorEmail)) {
            throw new SecurityException("Brak uprawnień do edycji tego ogłoszenia");
        }

        ad.setTitle(dto.getTitle());
        ad.setDescription(dto.getDescription());
        ad.setLocation(dto.getLocation());
        ad.setCategory(categoryRepository.findById(dto.getCategoryId()).orElseThrow());
        if (dto.getImageFiles() != null && !dto.getImageFiles().isEmpty() && !dto.getImageFiles().get(0).isEmpty()) {
            List<String> newImages = fileStorageService.storeFiles(dto.getImageFiles());
            ad.setImageUrls(newImages);
        }

        adRepository.save(ad);
    }
    @Transactional
    public List<Ad> findActiveAdsByUserId(Long userId) {
        return adRepository.findAllByAuthorIdAndStatusOrderByCreatedAtDesc(userId, AdStatusEnum.ACTIVE);
    }


}
