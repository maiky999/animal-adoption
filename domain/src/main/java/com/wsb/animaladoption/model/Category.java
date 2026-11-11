package com.wsb.animaladoption.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Translation key of the category name, e.g. "Gady i płazy" -> "category.gady_i_płazy" (i18n/common bundle).
     * Categories added later in the database, without a translation, are displayed under their original name.
     */
    public String getMessageKey() {
        return "category." + name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
