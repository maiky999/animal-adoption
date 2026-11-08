package com.wsb.animaladoption.service;

import com.wsb.animaladoption.service.impl.AdServiceImpl;
import com.wsb.animaladoption.config.RabbitMQConfig;
import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdSearchCriteria;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.enums.AnimalSexEnum;
import com.wsb.animaladoption.event.AdModerationEvent;
import com.wsb.animaladoption.event.AdoptionFormEvent;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.AdRepository;
import com.wsb.animaladoption.repository.CategoryRepository;
import com.wsb.animaladoption.repository.MessageRepository;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.exception.LocalizedException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

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
    private MessageRepository messageRepository;

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

        adService.changeAdStatus(10L, AdStatusEnum.ADOPTED, "autor@example.com");

        assertEquals(AdStatusEnum.ADOPTED, sampleAd.getStatus());
        verify(adRepository, times(1)).save(sampleAd);
    }

    @Test
    @DisplayName("Should throw SecurityException when another user tries to edit")
    void shouldThrowSecurityExceptionWhenAuthorDoesNotMatch() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        assertThrows(SecurityException.class, () ->
                adService.changeAdStatus(10L, AdStatusEnum.ARCHIVED, "obcy@example.com")
        );
        verify(adRepository, never()).save(any());
    }

    @Test
    @DisplayName("The author must not publish a rejected listing themselves (that would skip moderation)")
    void shouldNotLetAuthorPublishRejectedAd() {
        sampleAd.setStatus(AdStatusEnum.REJECTED);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        assertThrows(IllegalArgumentException.class, () ->
                adService.changeAdStatus(10L, AdStatusEnum.ACTIVE, "autor@example.com"));

        assertEquals(AdStatusEnum.REJECTED, sampleAd.getStatus());
        verify(adRepository, never()).save(any());
    }

    @Test
    @DisplayName("Only a published listing can be marked as adopted")
    void shouldNotMarkPendingAdAsAdopted() {
        sampleAd.setStatus(AdStatusEnum.PENDING);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        assertThrows(IllegalArgumentException.class, () ->
                adService.changeAdStatus(10L, AdStatusEnum.ADOPTED, "autor@example.com"));
        verify(adRepository, never()).save(any());
    }

    @Test
    @DisplayName("The author can close a rejected listing")
    void shouldArchiveRejectedAd() {
        sampleAd.setStatus(AdStatusEnum.REJECTED);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        adService.changeAdStatus(10L, AdStatusEnum.ARCHIVED, "autor@example.com");

        assertEquals(AdStatusEnum.ARCHIVED, sampleAd.getStatus());
    }

    @Test
    @DisplayName("Should approve a listing by a moderator and clear the previous rejection reason")
    void shouldApproveAd() {
        sampleAd.setStatus(AdStatusEnum.PENDING);
        sampleAd.setRejectionReason("Brak zdjęć");
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        adService.approveAd(10L);

        assertEquals(AdStatusEnum.ACTIVE, sampleAd.getStatus());
        assertNull(sampleAd.getRejectionReason());
        verify(adRepository, times(1)).save(sampleAd);
    }

    @Test
    @DisplayName("Rejecting saves the reason and sends it to the author by email (via the queue)")
    void shouldRejectAdWithReasonAndNotifyAuthor() {
        sampleAd.setStatus(AdStatusEnum.PENDING);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));
        when(translationService.getDefaultLocale()).thenReturn(Locale.of("pl"));

        adService.rejectAd(10L, "  Zdjęcia nie przedstawiają zwierzęcia  ");

        assertEquals(AdStatusEnum.REJECTED, sampleAd.getStatus());
        assertEquals("Zdjęcia nie przedstawiają zwierzęcia", sampleAd.getRejectionReason());

        ArgumentCaptor<AdModerationEvent> captor = ArgumentCaptor.forClass(AdModerationEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_AD_MODERATION), captor.capture());
        assertEquals("autor@example.com", captor.getValue().getAuthorEmail());
        assertEquals("Piesek szuka domu", captor.getValue().getAdTitle());
        assertEquals("Zdjęcia nie przedstawiają zwierzęcia", captor.getValue().getReason());
        assertEquals(AdModerationEvent.Action.REJECTED, captor.getValue().getAction());
    }

    @Test
    @DisplayName("A rejection without a reason is not accepted")
    void shouldNotRejectWithoutReason() {
        assertThrows(IllegalArgumentException.class, () -> adService.rejectAd(10L, "   "));
        verify(adRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    // --- Removing listings by a moderator ---

    @Test
    @DisplayName("Removing a published listing hides it, saves the reason and emails the author")
    void shouldRemoveActiveAdAndNotifyAuthor() {
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));
        when(translationService.getDefaultLocale()).thenReturn(Locale.of("pl"));

        adService.removeAd(10L, " Sprzedaż zwierzęcia ");

        assertEquals(AdStatusEnum.REMOVED, sampleAd.getStatus());
        assertEquals("Sprzedaż zwierzęcia", sampleAd.getRemovalReason());
        verify(adRepository).save(sampleAd);

        ArgumentCaptor<AdModerationEvent> captor = ArgumentCaptor.forClass(AdModerationEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_EMAIL),
                eq(RabbitMQConfig.ROUTING_KEY_AD_MODERATION), captor.capture());
        assertEquals(AdModerationEvent.Action.REMOVED, captor.getValue().getAction());
        assertEquals("Sprzedaż zwierzęcia", captor.getValue().getReason());
    }

    @Test
    @DisplayName("Removing without a reason or a second time is not possible")
    void shouldNotRemoveWithoutReasonOrTwice() {
        assertThrows(IllegalArgumentException.class, () -> adService.removeAd(10L, " "));

        sampleAd.setStatus(AdStatusEnum.REMOVED);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));
        assertThrows(LocalizedException.class, () -> adService.removeAd(10L, "Powód"));

        verify(adRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("The author cannot bring a removed listing back by editing it (that would send it to moderation again)")
    void shouldNotEditRemovedAd() {
        sampleAd.setStatus(AdStatusEnum.REMOVED);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        AdCreateDto dto = new AdCreateDto();
        dto.setTitle("Nowy tytuł ogłoszenia");

        assertThrows(LocalizedException.class, () -> adService.updateAd(10L, dto, "autor@example.com"));
        assertEquals(AdStatusEnum.REMOVED, sampleAd.getStatus());
        verify(adRepository, never()).save(any());
    }

    @Test
    @DisplayName("The author cannot close a removed listing either")
    void shouldNotArchiveRemovedAd() {
        sampleAd.setStatus(AdStatusEnum.REMOVED);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        assertThrows(IllegalArgumentException.class, () ->
                adService.changeAdStatus(10L, AdStatusEnum.ARCHIVED, "autor@example.com"));
    }

    @Test
    @DisplayName("Blocking an account removes all of its listings that are not removed yet, without emails")
    void shouldRemoveAllAdsOfAuthor() {
        Ad pending = Ad.builder().id(11L).author(sampleUser).status(AdStatusEnum.PENDING).build();
        Ad adopted = Ad.builder().id(12L).author(sampleUser).status(AdStatusEnum.ADOPTED).build();
        when(adRepository.findAllByAuthorIdAndStatusNot(1L, AdStatusEnum.REMOVED))
                .thenReturn(List.of(sampleAd, pending, adopted));

        int removed = adService.removeAllAdsOfAuthor(1L, "Konto zablokowane");

        assertEquals(3, removed);
        for (Ad ad : List.of(sampleAd, pending, adopted)) {
            assertEquals(AdStatusEnum.REMOVED, ad.getStatus());
            assertEquals("Konto zablokowane", ad.getRemovalReason());
        }
        verify(adRepository).saveAll(List.of(sampleAd, pending, adopted));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Counts listings waiting for moderation")
    void shouldCountPendingAds() {
        when(adRepository.countByStatus(AdStatusEnum.PENDING)).thenReturn(3L);

        assertEquals(3L, adService.countPendingAds());
    }

    @Test
    @DisplayName("Search asks for one page, newest first by default")
    @SuppressWarnings("unchecked")
    void shouldSearchOnePageNewestFirst() {
        when(adRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        adService.searchActiveAds(new AdSearchCriteria(), -3);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(adRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber(), "a negative page number is treated as the first page");
        assertEquals(AdService.PAGE_SIZE, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    @DisplayName("Creating a listing saves the information about the animal")
    void shouldSaveAnimalDetailsOnCreate() {
        Category category = new Category(1L, "Psy");
        when(userRepository.findByEmail("autor@example.com")).thenReturn(Optional.of(sampleUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(adRepository.save(any(Ad.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdCreateDto dto = new AdCreateDto();
        dto.setTitle("Burek szuka domu");
        dto.setDescription("Spokojny pies szuka kochającego domu.");
        dto.setCategoryId(1L);
        dto.setLocation("Kraków");
        dto.setAge(AnimalAgeEnum.SENIOR);
        dto.setSex(AnimalSexEnum.MALE);
        dto.setVaccinated(true);
        dto.setChipped(false);

        Ad saved = adService.createAd(dto, "autor@example.com");

        assertEquals(AnimalAgeEnum.SENIOR, saved.getAge());
        assertEquals(AnimalSexEnum.MALE, saved.getSex());
        assertNull(saved.getSize());
        assertTrue(saved.getVaccinated());
        assertFalse(saved.getChipped());
        assertEquals(AdStatusEnum.PENDING, saved.getStatus());
    }

    @Test
    @DisplayName("Should send a message to RabbitMQ when processing the adoption form")
    void shouldProcessAdoptionFormAndSendEvent() {
        sampleUser.setUserType(UserTypeEnum.ORGANIZATION);
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

    @Test
    @DisplayName("Should reject the adoption form when the listing author is a private person")
    void shouldRejectAdoptionFormForPrivateAuthor() {
        sampleUser.setUserType(UserTypeEnum.PRIVATE);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        LocalizedException ex = assertThrows(LocalizedException.class,
                () -> adService.processAdoptionForm(10L, new AdoptionFormDto()));

        assertEquals("adoption.form.unavailable", ex.getMessageKey());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Should reject the adoption form when the listing is not active")
    void shouldRejectAdoptionFormForInactiveAd() {
        sampleUser.setUserType(UserTypeEnum.ORGANIZATION);
        sampleAd.setStatus(AdStatusEnum.PENDING);
        when(adRepository.findById(10L)).thenReturn(Optional.of(sampleAd));

        assertThrows(LocalizedException.class, () -> adService.processAdoptionForm(10L, new AdoptionFormDto()));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("canView - an active listing is visible to everyone, including anonymous visitors")
    void activeAdShouldBeVisibleToEveryone() {
        assertTrue(adService.canView(sampleAd, null, false));
    }

    @Test
    @DisplayName("canView - a pending listing is hidden from strangers, but visible to the author and moderators")
    void pendingAdShouldBeHiddenFromStrangers() {
        sampleAd.setStatus(AdStatusEnum.PENDING);
        when(messageRepository.existsConversationAboutAd(10L, "obcy@example.com")).thenReturn(false);

        assertFalse(adService.canView(sampleAd, null, false), "anonim");
        assertFalse(adService.canView(sampleAd, "obcy@example.com", false), "obcy użytkownik");
        assertTrue(adService.canView(sampleAd, "autor@example.com", false), "autor");
        assertTrue(adService.canView(sampleAd, "moderator@example.com", true), "moderator");
    }

    @Test
    @DisplayName("canView - a chat participant still sees a listing that is no longer active")
    void inactiveAdShouldBeVisibleToChatParticipant() {
        sampleAd.setStatus(AdStatusEnum.ADOPTED);
        when(messageRepository.existsConversationAboutAd(10L, "rozmowca@example.com")).thenReturn(true);

        assertTrue(adService.canView(sampleAd, "rozmowca@example.com", false));
    }
}