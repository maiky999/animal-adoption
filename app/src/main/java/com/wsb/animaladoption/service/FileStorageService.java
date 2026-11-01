package com.wsb.animaladoption.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Abstrakcja przechowywania plików przesyłanych przez użytkowników (zdjęcia ogłoszeń, avatary, grafiki artykułów).
 * Obecna implementacja zapisuje pliki na dysku lokalnym, ale dzięki interfejsowi można ją podmienić
 * np. na przechowywanie w chmurze bez zmian w pozostałych warstwach aplikacji.
 */
public interface FileStorageService {

    /**
     * Zapisuje plik.
     *
     * @return publiczny adres URL pliku lub {@code null}, gdy plik jest pusty
     */
    String storeFile(MultipartFile file);

    /** Usuwa plik o podanym adresie URL (brak pliku nie jest błędem). */
    void deleteFile(String fileUrl);

    /** Zapisuje wiele plików, pomijając puste, i zwraca ich adresy URL. */
    List<String> storeFiles(List<MultipartFile> files);
}
