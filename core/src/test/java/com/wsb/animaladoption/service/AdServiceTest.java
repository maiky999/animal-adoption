package com.wsb.animaladoption.service;

import com.wsb.animaladoption.service.impl.AdServiceImpl;
import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.event.AdoptionFormEvent;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.CategoryRepository;
import com.wsb.animaladoption.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.wsb.translations.TranslationService;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdServiceTest {

    @Mock
    private AdRepository adRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private AdServiceImpl adService;

    private User sampleUser;
    private Ad sampleAd;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("autor@example.com")
                .displayName("Jan Kowalski")
                .build();

        sampleAd = Ad.builder()
                .id(10L)
                .title("Piesek szuka domu")
                .description("Opis")
                .location("Warszawa")
                .author(sampleUser)
                .status(AdStatusEnum.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should find a listing by ID")
    void shouldFindAdById() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        Ad result = adService.findById(10L);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("Piesek szuka domu", result.getTitle());
        verify(adRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when the listing does not exist")
    void shouldThrowExceptionWhenAdNotFound() {
        when(adRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> adService.findById(99L));
        verify(adRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("Should change the listing status when the author is correct")
    void shouldChangeAdStatusSuccessfully() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        adService.changeAdStatus(10L, AdStatusEnum.REJECTED, "autor@example.com");

        assertEquals(AdStatusEnum.REJECTED, sampleAd.getStatus());
        verify(adRepository, times(1)).save(sampleAd);
    }

    @Test
    @DisplayName("Should throw SecurityException when another user tries to edit")
    void shouldThrowSecurityExceptionWhenAuthorDoesNotMatch() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        assertThrows(SecurityException.class, () ->
                adService.changeAdStatus(10L, AdStatusEnum.REJECTED, "obcy@example.com")
        );
        verify(adRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should approve a listing by a moderator")
    void shouldModerateAdApprove() {
        sampleAd.setStatus(AdStatusEnum.PENDING);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        adService.moderateAd(10L, true);

        assertEquals(AdStatusEnum.ACTIVE, sampleAd.getStatus());
        verify(adRepository, times(1)).save(sampleAd);
    }

    @Test
    @DisplayName("Should send a message to RabbitMQ when processing the adoption form")
    void shouldProcessAdoptionFormAndSendEvent() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));
        when(translationService.getDefaultLocale()).thenReturn(Locale.of("pl"));

        AdoptionFormDto dto = new AdoptionFormDto();
        dto.setFirstName("Anna");
        dto.setLastName("Nowak");
        dto.setEmail("anna@example.com");
        dto.setPhone("123456789");
        dto.setCity("Kraków");
        dto.setAddress("Ulica 1");
        dto.setBirthYear(1995);

        adService.processAdoptionForm(10L, dto);

        ArgumentCaptor<AdoptionFormEvent> eventCaptor = ArgumentCaptor.forClass(AdoptionFormEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_ADOPTION_FORM),
                eventCaptor.capture()
        );
        // The organization's language is unknown - the questionnaire is sent to it in the default language
        assertEquals("pl", eventCaptor.getValue().getLanguageTag());
    }
}