package com.wsb.animaladoption.service.impl;

import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    /** Users may only upload images - only these extensions and MIME types are accepted. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /** Scales JPG/PNG photos down before saving (see ImageOptimizer for the reasons) */
    private final ImageOptimizer imageOptimizer;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String extension = validateImage(file);

        try {
            // The name on disk is only a random UUID + extension. The original name is not used,
            // because it may contain special characters, path fragments (../) or personal data.
            String filename = UUID.randomUUID() + "." + extension;
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetLocation = uploadPath.resolve(filename);
            Files.createDirectories(uploadPath);
            if (imageOptimizer.supports(extension)) {
                imageOptimizer.optimizeAndSave(file.getBytes(), extension, targetLocation);
            } else {
                // GIF and WEBP are stored exactly as they were sent
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Nie udało się zapisać pliku", e);
        }
    }

    /**
     * Checks that the file is an allowed image (both the extension and the MIME type sent by the browser).
     *
     * @return the normalized file extension (lowercase)
     * @throws LocalizedException if the file is not an allowed image
     */
    private String validateImage(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);

        if (!ALLOWED_EXTENSIONS.contains(extension) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new LocalizedException("validation.file.invalidType");
        }
        // "jpeg" and "jpg" are the same format - one extension keeps the files consistent
        return extension.equals("jpeg") ? "jpg" : extension;
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return;
        }

        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName);

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Usunięto stary plik: %s".formatted(fileName));
            }
        } catch (IOException e) {
            log.error("Nie udało się usunąć pliku: %s".formatted(fileUrl));
        }
    }

    @Override
    public List<String> storeFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        List<MultipartFile> nonEmpty = files.stream()
                .filter(file -> !file.isEmpty())
                .toList();

        // First validate all files, so that a wrong file at the end of the list
        // does not leave the earlier ones saved on disk without a listing
        nonEmpty.forEach(this::validateImage);

        // A mutable list, because it is assigned directly to the entity's @ElementCollection
        List<String> urls = new ArrayList<>();
        try {
            for (MultipartFile file : nonEmpty) {
                urls.add(storeFile(file));
            }
        } catch (RuntimeException e) {
            // Whether the content is a correct image is known only while saving (ImageOptimizer) -
            // if e.g. the third file is damaged, remove the two already saved, so no files are left without a listing
            urls.forEach(this::deleteFile);
            throw e;
        }
        return urls;
    }

}
