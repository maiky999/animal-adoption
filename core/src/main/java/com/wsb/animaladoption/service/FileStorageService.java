package com.wsb.animaladoption.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Abstraction for storing files uploaded by users (listing photos, avatars, article images).
 * The current implementation saves files on the local disk, but thanks to the interface it can be replaced,
 * e.g. with cloud storage, without changes in the other application layers.
 */
public interface FileStorageService {

    /**
     * Saves an image file (JPG, PNG, GIF or WEBP) under a random name.
     * JPG and PNG photos are scaled down and saved without metadata (EXIF) first.
     *
     * @return public URL of the file, or {@code null} if the file is empty
     * @throws com.wsb.animaladoption.exception.LocalizedException if the file is not an allowed image,
     *                                                             is damaged or has too big dimensions
     */
    String storeFile(MultipartFile file);

    /** Deletes the file with the given URL (a missing file is not an error). */
    void deleteFile(String fileUrl);

    /**
     * Saves multiple files, skipping empty ones, and returns their URLs.
     * All or nothing - if one file is rejected, none of them stays on disk.
     */
    List<String> storeFiles(List<MultipartFile> files);
}
