package com.wsb.animaladoption.service.impl;

import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdSearchCriteria;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.event.AdRejectedEvent;
import com.wsb.animaladoption.event.AdoptionFormEvent;
import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.CategoryRepository;
import com.wsb.animaladoption.repository.MessageRepository;
import com.wsb.animaladoption.repository.UserRepository;
import com.wsb.animaladoption.service.AdService;
import com.wsb.animaladoption.service.FileStorageService;
import com.wsb.animaladoption.specification.AdSpecifications;
import com.wsb.translations.TranslationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdServiceImpl implements AdService {
    private final AdRepository adRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final FileStorageService fileStorageService;
    private final RabbitTemplate rabbitTemplate;
    private final TranslationService translationService;

    @Override
    public List<Ad> findActiveAds(Long categoryId) {
        if (categoryId != null) {
            return adRepository.findAllByCategoryIdAndStatusOrderByCreatedAtDesc(categoryId, AdStatusEnum.ACTIVE);
        }
        return adRepository.findAllByStatusOrderByCreatedAtDesc(AdStatusEnum.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Ad> searchActiveAds(AdSearchCriteria criteria, int page) {
        Sort.Direction direction = criteria.getSort() == AdSearchCriteria.Sort.OLDEST
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        // The id as the second sort key makes the order stable when two listings have the same date,
        // so a listing never shows up on two pages (or on none) while browsing
        Sort sort = Sort.by(direction, "createdAt").and(Sort.by(direction, "id"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort);
        return adRepository.findAll(AdSpecifications.activeMatching(criteria), pageable);
    }

    @Override
    public Ad findById(Long id) {
        return adRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono ogłoszenia"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canView(Ad ad, String viewerEmail, boolean viewerIsModerator) {
        if (ad.getStatus() == AdStatusEnum.ACTIVE) {
            return true;
        }

        if (viewerIsModerator) {
            return true;
        }
        if (viewerEmail == null) {
            return false;
        }

        if (ad.getAuthor().getEmail().equalsIgnoreCase(viewerEmail)) {
            return true;
        }

        return messageRepository.existsConversationAboutAd(ad.getId(), viewerEmail);
    }

    @Override
    public List<Ad> findUserAds(String authorEmail) {
        return adRepository.findAllByAuthorEmailOrderByCreatedAtDesc(authorEmail);
    }

    @Override
    public List<Ad> findPendingAds() {
        return adRepository.findAllByStatusOrderByCreatedAtDesc(AdStatusEnum.PENDING);
    }

    @Override
    @Transactional
    public void changeAdStatus(Long adId, AdStatusEnum newStatus, String authorEmail) {
        Ad ad = findById(adId);

        if (!ad.getAuthor().getEmail().equals(authorEmail)) {
            throw new SecurityException("Brak uprawnień do edycji tego ogłoszenia");
        }

        // Without this check the author could send status=ACTIVE in a hand-crafted request
        // and publish a pending or rejected listing, skipping moderation
        boolean allowed = switch (newStatus) {
            case ARCHIVED -> ad.getStatus() != AdStatusEnum.ADOPTED && ad.getStatus() != AdStatusEnum.ARCHIVED;
            case ADOPTED -> ad.getStatus() == AdStatusEnum.ACTIVE;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Niedozwolona zmiana statusu: " + ad.getStatus() + " -> " + newStatus);
        }

        ad.setStatus(newStatus);
        adRepository.save(ad);
    }

    @Override
    @Transactional
    public void approveAd(Long adId) {
        Ad ad = findById(adId);
        ad.setStatus(AdStatusEnum.ACTIVE);
        ad.setRejectionReason(null);
        adRepository.save(ad);
    }

    @Override
    @Transactional
    public void rejectAd(Long adId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Powód odrzucenia jest wymagany");
        }
        Ad ad = findById(adId);
        ad.setStatus(AdStatusEnum.REJECTED);
        ad.setRejectionReason(reason.trim());
        adRepository.save(ad);

        AdRejectedEvent event = new AdRejectedEvent(
                ad.getAuthor().getEmail(),
                ad.getAuthor().getDisplayName(),
                ad.getTitle(),
                ad.getRejectionReason(),
                // We don't know which language the author uses - the email is sent in the default language
                translationService.getDefaultLocale().toLanguageTag());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EMAIL, RabbitMQConfig.ROUTING_KEY_AD_REJECTED, event);
    }

    @Override
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
                .status(AdStatusEnum.PENDING)
                .build();
        dto.applyAnimalDetailsTo(ad);

        return adRepository.save(ad);
    }

    @Override
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
        dto.applyAnimalDetailsTo(ad);
        if (dto.getImageFiles() != null && !dto.getImageFiles().isEmpty() && !dto.getImageFiles().get(0).isEmpty()) {
            List<String> newImages = fileStorageService.storeFiles(dto.getImageFiles());
            ad.setImageUrls(newImages);
        }

        ad.setStatus(AdStatusEnum.PENDING);

        adRepository.save(ad);
    }

    @Override
    @Transactional
    public List<Ad> findActiveAdsByUserId(Long userId) {
        return adRepository.findAllByAuthorIdAndStatusOrderByCreatedAtDesc(userId, AdStatusEnum.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public void processAdoptionForm(Long adId, AdoptionFormDto dto) {
        Ad ad = findById(adId);

        // The questionnaire exists only for organizations (private persons are contacted via chat)
        // and only for listings that are currently published
        if (ad.getAuthor().getUserType() != UserTypeEnum.ORGANIZATION || ad.getStatus() != AdStatusEnum.ACTIVE) {
            throw new LocalizedException("adoption.form.unavailable");
        }

        AdoptionFormEvent event = new AdoptionFormEvent(
                ad.getAuthor().getEmail(),
                ad.getAuthor().getDisplayName(),
                ad.getTitle(),
                dto.getFirstName(),
                dto.getLastName(),
                dto.getAddress(),
                dto.getCity(),
                dto.getEmail(),
                dto.getPhone(),
                dto.getBirthYear(),
                // The recipient is an organization whose language we don't know - the email is sent in the default language
                translationService.getDefaultLocale().toLanguageTag()
        );

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EMAIL, RabbitMQConfig.ROUTING_KEY_ADOPTION_FORM, event);
    }

    @Override
    public List<Category> findAllCategories() {
        return categoryRepository.findAllByOrderByIdAsc();
    }
}