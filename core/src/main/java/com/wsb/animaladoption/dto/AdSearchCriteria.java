package com.wsb.animaladoption.dto;

import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.enums.AnimalSexEnum;
import com.wsb.animaladoption.enums.AnimalSizeEnum;
import lombok.Data;

/**
 * Filters of the listings board. Spring fills the object from the URL parameters (e.g. {@code /ads?q=burek&age=YOUNG}),
 * so every search result has its own link that can be bookmarked or shared.
 * Every field is optional - an empty field simply does not narrow the results.
 */
@Data
public class AdSearchCriteria {

    public enum Sort {
        NEWEST,
        OLDEST
    }

    private Long categoryId;

    /** Text searched in the listing title. */
    private String q;

    /** Part of the location, e.g. "Kraków". */
    private String location;

    private AnimalAgeEnum age;

    private AnimalSexEnum sex;

    private AnimalSizeEnum size;

    // Features - true means "show only animals that have this feature confirmed"; null - no filter
    private Boolean vaccinated;

    private Boolean sterilized;

    private Boolean chipped;

    private Boolean goodWithKids;

    private Boolean goodWithAnimals;

    private Sort sort = Sort.NEWEST;

    /** Whether any filter is set (the view then shows a "clear filters" link). */
    public boolean isFiltered() {
        return categoryId != null || hasText(q) || hasText(location) || age != null || sex != null || size != null
                || Boolean.TRUE.equals(vaccinated) || Boolean.TRUE.equals(sterilized) || Boolean.TRUE.equals(chipped)
                || Boolean.TRUE.equals(goodWithKids) || Boolean.TRUE.equals(goodWithAnimals);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
