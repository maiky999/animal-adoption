package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;

import java.util.List;

/**
 * Business logic for adoption listings: searching, creating, editing,
 * moderation and handling pre-adoption questionnaires.
 */
public interface AdService {

    /**
     * Returns active listings, optionally narrowed down to a category.
     *
     * @param categoryId category id, or {@code null} to fetch all categories
     */
    List<Ad> findActiveAds(Long categoryId);

    /**
     * Returns the listing with the given id.
     *
     * @throws IllegalArgumentException if the listing does not exist
     */
    Ad findById(Long id);

    /**
     * Checks whether the given person may see the listing details.
     * Active listings are public; other statuses (pending, rejected, adopted, archived) are visible only
     * to the author, moderators and users who already have a conversation about this listing.
     *
     * @param viewerEmail       email of the logged-in user, or {@code null} for an anonymous visitor
     * @param viewerIsModerator {@code true} if the viewer has the MODERATOR or ADMIN role
     */
    boolean canView(Ad ad, String viewerEmail, boolean viewerIsModerator);

    /** Returns all listings of the given author (regardless of status). */
    List<Ad> findUserAds(String authorEmail);

    /** Returns listings awaiting moderator review. */
    List<Ad> findPendingAds();

    /**
     * Changes the listing status by its author (e.g. marking it as adopted).
     *
     * @throws SecurityException if the user is not the author of the listing
     */
    void changeAdStatus(Long adId, AdStatusEnum newStatus, String authorEmail);

    /**
     * Approves or rejects a listing as part of moderation.
     *
     * @param approve {@code true} - the listing becomes active, {@code false} - it is rejected
     */
    void moderateAd(Long adId, boolean approve);

    /** Creates a new listing with a status awaiting moderation. */
    Ad createAd(AdCreateDto dto, String authorEmail);

    /**
     * Updates the listing and sends it back to moderation.
     *
     * @throws SecurityException if the user is not the author of the listing
     */
    void updateAd(Long id, AdCreateDto dto, String authorEmail);

    /** Returns the user's active listings (e.g. for the public profile). */
    List<Ad> findActiveAdsByUserId(Long userId);

    /**
     * Forwards the pre-adoption questionnaire to the listing author (asynchronously, via the queue).
     *
     * @throws com.wsb.animaladoption.exception.LocalizedException if the author is not an organization
     *                                                             or the listing is not active
     */
    void processAdoptionForm(Long adId, AdoptionFormDto dto);

    /** Returns all listing categories sorted by id (for filters and forms). */
    List<Category> findAllCategories();
}
