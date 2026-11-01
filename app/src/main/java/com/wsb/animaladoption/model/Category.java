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
     * Klucz tłumaczenia nazwy kategorii, np. "Gady i płazy" -> "category.gady_i_płazy" (paczka i18n/common).
     * Kategorie dodane później w bazie, bez tłumaczenia, wyświetlają się pod oryginalną nazwą.
     */
    public String getMessageKey() {
        return "category." + name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
