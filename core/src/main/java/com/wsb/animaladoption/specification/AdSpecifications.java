package com.wsb.animaladoption.specification;

import com.wsb.animaladoption.dto.AdSearchCriteria;
import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.model.Ad;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the database query for the listings board from the filters chosen by the user.
 * <p>
 * Why a Specification instead of repository methods like {@code findAllByCategoryIdAndAgeAnd...}:
 * with a dozen optional filters there would be thousands of combinations. Here each filter adds its own
 * condition only when it is set, and all conditions are joined with AND into one SQL query.
 */
public final class AdSpecifications {

    /** Escape character for LIKE patterns (see {@link #containsIgnoreCase}). */
    private static final char LIKE_ESCAPE = '\\';

    private AdSpecifications() {
    }

    /** Active listings matching all the filters that are set. */
    public static Specification<Ad> activeMatching(AdSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();

            // The public board shows only published listings - regardless of the filters
            conditions.add(cb.equal(root.get("status"), AdStatusEnum.ACTIVE));

            if (criteria.getCategoryId() != null) {
                conditions.add(cb.equal(root.get("category").get("id"), criteria.getCategoryId()));
            }
            if (hasText(criteria.getQ())) {
                conditions.add(containsIgnoreCase(cb, root.get("title"), criteria.getQ()));
            }
            if (hasText(criteria.getLocation())) {
                conditions.add(containsIgnoreCase(cb, root.get("location"), criteria.getLocation()));
            }
            if (criteria.getAge() != null) {
                conditions.add(cb.equal(root.get("age"), criteria.getAge()));
            }
            if (criteria.getSex() != null) {
                conditions.add(cb.equal(root.get("sex"), criteria.getSex()));
            }
            if (criteria.getSize() != null) {
                conditions.add(cb.equal(root.get("size"), criteria.getSize()));
            }
            addFeature(conditions, cb, root, "vaccinated", criteria.getVaccinated());
            addFeature(conditions, cb, root, "sterilized", criteria.getSterilized());
            addFeature(conditions, cb, root, "chipped", criteria.getChipped());
            addFeature(conditions, cb, root, "goodWithKids", criteria.getGoodWithKids());
            addFeature(conditions, cb, root, "goodWithAnimals", criteria.getGoodWithAnimals());

            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }

    /** A ticked feature filter shows only listings where the author confirmed that feature. */
    private static void addFeature(List<Predicate> conditions, CriteriaBuilder cb, Root<Ad> root,
                                   String field, Boolean required) {
        if (Boolean.TRUE.equals(required)) {
            conditions.add(cb.isTrue(root.get(field)));
        }
    }

    /**
     * Case-insensitive "contains". Characters with a special meaning in LIKE ({@code % _ [}) are escaped,
     * so typing e.g. "100%" searches for that text instead of acting as a wildcard.
     * The value is passed as a query parameter, never glued into SQL - so there is no risk of SQL injection.
     */
    private static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> field, String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
        return cb.like(cb.lower(field), "%" + escaped + "%", LIKE_ESCAPE);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
