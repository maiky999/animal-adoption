package com.wsb.animaladoption.model;

import com.wsb.animaladoption.enums.AdStatusEnum;
import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.enums.AnimalSexEnum;
import com.wsb.animaladoption.enums.AnimalSizeEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ads")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false)
    private String location;

    @ElementCollection
    @CollectionTable(name ="ad_images", joinColumns = @JoinColumn(name = "ad_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private AdStatusEnum status;

    /** Reason given by the moderator when rejecting the listing (shown to the author); cleared on approval. */
    @Column(length = 500)
    private String rejectionReason;

    /** Reason given by the moderator when removing the listing (status REMOVED) - shown to the author. */
    @Column(length = 500)
    private String removalReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // --- Information about the animal ---
    // All fields are optional (nullable): null means "not specified". This also lets Hibernate (ddl-auto: update)
    // add the columns to an existing table that already contains listings.

    @Enumerated(EnumType.STRING)
    @Column(name = "animal_age", length = 10)
    private AnimalAgeEnum age;

    @Enumerated(EnumType.STRING)
    @Column(name = "animal_sex", length = 10)
    private AnimalSexEnum sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "animal_size", length = 10)
    private AnimalSizeEnum size;

    // Boolean (not boolean), so the column is nullable - for the features, null and false both mean "not confirmed"

    private Boolean vaccinated;

    private Boolean sterilized;

    private Boolean chipped;

    private Boolean goodWithKids;

    private Boolean goodWithAnimals;

    /** Whether any animal information was filled in - used by the view to decide if the section is shown. */
    public boolean hasAnimalDetails() {
        return age != null || sex != null || size != null || hasAnyFeature();
    }

    /** Whether at least one of the yes/no features is confirmed. */
    public boolean hasAnyFeature() {
        return Boolean.TRUE.equals(vaccinated) || Boolean.TRUE.equals(sterilized) || Boolean.TRUE.equals(chipped)
                || Boolean.TRUE.equals(goodWithKids) || Boolean.TRUE.equals(goodWithAnimals);
    }
}
