package com.wsb.animaladoption.service;

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
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Collections;
import java.util.List;
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

    @InjectMocks
    private AdService adService;

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
    @DisplayName("Powinien znaleźć ogłoszenie po ID")
    void shouldFindAdById() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        Ad result = adService.findById(10L);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("Piesek szuka domu", result.getTitle());
        verify(adRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("Powinien rzucić IllegalArgumentException gdy ogłoszenie nie istnieje")
    void shouldThrowExceptionWhenAdNotFound() {
        when(adRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> adService.findById(99L));
        verify(adRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("Powinien zmienić status ogłoszenia gdy autor jest poprawny")
    void shouldChangeAdStatusSuccessfully() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        adService.changeAdStatus(10L, AdStatusEnum.REJECTED, "autor@example.com");

        assertEquals(AdStatusEnum.REJECTED, sampleAd.getStatus());
        verify(adRepository, times(1)).save(sampleAd);
    }

    @Test
    @DisplayName("Powinien rzucić SecurityException przy próbie edycji przez innego użytkownika")
    void shouldThrowSecurityExceptionWhenAuthorDoesNotMatch() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        assertThrows(SecurityException.class, () ->
                adService.changeAdStatus(10L, AdStatusEnum.REJECTED, "obcy@example.com")
        );
        verify(adRepository, never()).save(any());
    }

    @Test
    @DisplayName("Powinien zatwierdzić ogłoszenie przez moderatora")
    void shouldModerateAdApprove() {
        sampleAd.setStatus(AdStatusEnum.PENDING);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        adService.moderateAd(10L, true);

        assertEquals(AdStatusEnum.ACTIVE, sampleAd.getStatus());
        verify(adRepository, times(1)).save(sampleAd);
    }

    @Test
    @DisplayName("Powinien wysłać wiadomość do RabbitMQ podczas przetwarzania formularza adopcji")
    void shouldProcessAdoptionFormAndSendEvent() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        AdoptionFormDto dto = new AdoptionFormDto();
        dto.setFirstName("Anna");
        dto.setLastName("Nowak");
        dto.setEmail("anna@example.com");
        dto.setPhone("123456789");
        dto.setCity("Kraków");
        dto.setAddress("Ulica 1");
        dto.setBirthYear(1995);

        adService.processAdoptionForm(10L, dto);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_ADOPTION_FORM),
                any(AdoptionFormEvent.class)
        );
    }
}