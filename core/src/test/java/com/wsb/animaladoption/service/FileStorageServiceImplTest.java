package com.wsb.animaladoption.service;

import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.service.impl.FileStorageServiceImpl;
import com.wsb.animaladoption.service.impl.ImageOptimizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceImplTest {

    @TempDir
    Path uploadDir;

    private FileStorageServiceImpl fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageServiceImpl(new ImageOptimizer(1600, 0.85f, 40));
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", uploadDir.toString());
    }

    @Test
    @DisplayName("A JPG photo should be saved scaled down, under a random name with the .jpg extension")
    void shouldStoreScaledDownJpeg() throws IOException {
        byte[] photo = ImageOptimizerTest.image("jpg", 3200, 2400, BufferedImage.TYPE_INT_RGB);

        String url = fileStorageService.storeFile(new MockMultipartFile("file", "burek.jpeg", "image/jpeg", photo));

        assertThat(url).startsWith("/uploads/").endsWith(".jpg").doesNotContain("burek");
        BufferedImage saved = ImageIO.read(uploadDir.resolve(url.substring("/uploads/".length())).toFile());
        assertThat(saved.getWidth()).isEqualTo(1600);
    }

    @Test
    @DisplayName("A GIF should be saved unchanged (it may be animated)")
    void shouldStoreGifUnchanged() throws IOException {
        byte[] gif = ImageOptimizerTest.image("gif", 2000, 1000, BufferedImage.TYPE_BYTE_INDEXED);

        String url = fileStorageService.storeFile(new MockMultipartFile("file", "anim.gif", "image/gif", gif));

        assertThat(Files.readAllBytes(uploadDir.resolve(url.substring("/uploads/".length())))).isEqualTo(gif);
    }

    @Test
    @DisplayName("When one of several files is damaged, none of them should stay on disk")
    void shouldRemoveSavedFilesWhenOneOfThemIsDamaged() throws IOException {
        var good = new MockMultipartFile("files", "a.jpg", "image/jpeg",
                ImageOptimizerTest.image("jpg", 400, 300, BufferedImage.TYPE_INT_RGB));
        var damaged = new MockMultipartFile("files", "b.jpg", "image/jpeg",
                "to nie jest obraz".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fileStorageService.storeFiles(List.of(good, damaged)))
                .isInstanceOf(LocalizedException.class);

        try (Stream<Path> files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        }
    }
}
