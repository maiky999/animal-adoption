package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.AdCreateDto;
import com.wsb.animaladoption.dto.AdSearchCriteria;
import com.wsb.animaladoption.dto.AdoptionFormDto;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import com.wsb.animaladoption.model.Category;
import org.springframework.data.domain.Page;

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

    /** Number of listings on one page of the listings board. */
    int PAGE_SIZE = 12;

    /**
     * Searches active listings with the filters from the listings board, one page at a time.
     *
     * @param page page number counted from 0 (a negative number is treated as 0)
     */
    Page<Ad> searchActiveAds(AdSearchCriteria criteria, int page);

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
     * Changes the listing status by its author. The author can only close a listing ({@code ARCHIVED})
     * or mark a published one as adopted ({@code ADOPTED}) - publishing is reserved for moderators.
     *
     * @throws SecurityException        if the user is not the author of the listing
     * @throws IllegalArgumentException if the author may not set this status
     */
    void changeAdStatus(Long adId, AdStatusEnum newStatus, String authorEmail);

    /** Number of listings waiting for moderation (the badge in the navigation). */
    long countPendingAds();

    /**
     * Moderation: removes a listing (in any status) because it breaks the rules, and emails the author the reason.
     * The listing is hidden and can no longer be edited, but stays in the database for the conversations about it.
     *
     * @throws IllegalArgumentException if the reason is empty
     * @throws com.wsb.animaladoption.exception.LocalizedException if the listing has already been removed
     */
    void removeAd(Long adId, String reason);

    /**
     * Removes all listings of the author that are not removed yet - used when the author's account is blocked.
     * Unlike {@link #removeAd(Long, String)} no emails are sent (one per listing would be spam;
     * the blocked user sees the reason of the ban when trying to log in).
     *
     * @return number of removed listings
     */
    int removeAllAdsOfAuthor(Long authorId, String reason);

    /** Moderation: publishes the listing. */
    void approveAd(Long adId);

    /**
     * Moderation: rejects the listing and emails the author the reason, so they know what to correct.
     * The author can then edit the listing, which sends it back to moderation.
     */
    void rejectAd(Long adId, String reason);

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
