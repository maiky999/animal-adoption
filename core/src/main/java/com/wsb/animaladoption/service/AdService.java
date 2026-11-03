package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;

import java.util.List;

/**
 * Logika biznesowa związana z ogłoszeniami adopcyjnymi: wyszukiwanie, tworzenie, edycja,
 * moderacja oraz obsługa ankiet przedadopcyjnych.
 */
public interface AdService {

    /**
     * Zwraca aktywne ogłoszenia, opcjonalnie zawężone do kategorii.
     *
     * @param categoryId id kategorii lub {@code null}, aby pobrać wszystkie kategorie
     */
    List<Ad> findActiveAds(Long categoryId);

    /**
     * Zwraca ogłoszenie o podanym id.
     *
     * @throws IllegalArgumentException gdy ogłoszenie nie istnieje
     */
    Ad findById(Long id);

    /** Zwraca wszystkie ogłoszenia danego autora (niezależnie od statusu). */
    List<Ad> findUserAds(String authorEmail);

    /** Zwraca ogłoszenia oczekujące na weryfikację moderatora. */
    List<Ad> findPendingAds();

    /**
     * Zmienia status ogłoszenia przez jego autora (np. oznaczenie jako adoptowane).
     *
     * @throws SecurityException gdy użytkownik nie jest autorem ogłoszenia
     */
    void changeAdStatus(Long adId, AdStatusEnum newStatus, String authorEmail);

    /**
     * Akceptuje lub odrzuca ogłoszenie w ramach moderacji.
     *
     * @param approve {@code true} - ogłoszenie staje się aktywne, {@code false} - zostaje odrzucone
     */
    void moderateAd(Long adId, boolean approve);

    /** Tworzy nowe ogłoszenie ze statusem oczekującym na moderację. */
    Ad createAd(AdCreateDto dto, String authorEmail);

    /**
     * Aktualizuje ogłoszenie i ponownie kieruje je do moderacji.
     *
     * @throws SecurityException gdy użytkownik nie jest autorem ogłoszenia
     */
    void updateAd(Long id, AdCreateDto dto, String authorEmail);

    /** Zwraca aktywne ogłoszenia użytkownika (np. na potrzeby profilu publicznego). */
    List<Ad> findActiveAdsByUserId(Long userId);

    /** Przekazuje ankietę przedadopcyjną do autora ogłoszenia (asynchronicznie, przez kolejkę). */
    void processAdoptionForm(Long adId, AdoptionFormDto dto);

    /** Zwraca wszystkie kategorie ogłoszeń posortowane po id (do filtrów i formularzy). */
    List<Category> findAllCategories();
}
