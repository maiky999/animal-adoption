package com.wsb.animaladoption.service;

import com.wsb.animaladoption.exception.LocalizedException;
import com.wsb.animaladoption.service.impl.ImageOptimizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageOptimizerTest {

    /** The same settings as in application.yml */
    private final ImageOptimizer optimizer = new ImageOptimizer(1600, 0.85f, 40);

    @TempDir
    Path dir;

    @Test
    @DisplayName("A big photo should be scaled down to 1600 px on the longest side, keeping the proportions")
    void shouldScaleDownBigPhoto() throws IOException {
        byte[] original = image("jpg", 4000, 3000, BufferedImage.TYPE_INT_RGB);
        Path target = dir.resolve("photo.jpg");

        optimizer.optimizeAndSave(original, "jpg", target);

        BufferedImage saved = ImageIO.read(target.toFile());
        assertThat(saved.getWidth()).isEqualTo(1600);
        assertThat(saved.getHeight()).isEqualTo(1200);
        assertThat(Files.size(target)).isLessThan(original.length);
    }

    @Test
    @DisplayName("A vertical photo should be limited by its height")
    void shouldScaleDownVerticalPhoto() throws IOException {
        Path target = dir.resolve("photo.jpg");

        optimizer.optimizeAndSave(image("jpg", 1500, 3000, BufferedImage.TYPE_INT_RGB), "jpg", target);

        BufferedImage saved = ImageIO.read(target.toFile());
        assertThat(saved.getWidth()).isEqualTo(800);
        assertThat(saved.getHeight()).isEqualTo(1600);
    }

    @Test
    @DisplayName("A small photo should keep its size - it is never enlarged")
    void shouldNotEnlargeSmallPhoto() throws IOException {
        Path target = dir.resolve("photo.jpg");

        optimizer.optimizeAndSave(image("jpg", 800, 600, BufferedImage.TYPE_INT_RGB), "jpg", target);

        BufferedImage saved = ImageIO.read(target.toFile());
        assertThat(saved.getWidth()).isEqualTo(800);
        assertThat(saved.getHeight()).isEqualTo(600);
    }

    @Test
    @DisplayName("A PNG should stay a PNG with its transparency")
    void shouldKeepPngTransparency() throws IOException {
        Path target = dir.resolve("logo.png");

        optimizer.optimizeAndSave(image("png", 2000, 1000, BufferedImage.TYPE_INT_ARGB), "png", target);

        BufferedImage saved = ImageIO.read(target.toFile());
        assertThat(saved.getWidth()).isEqualTo(1600);
        assertThat(saved.getColorModel().hasAlpha()).isTrue();
        // image() leaves the top-left corner fully transparent
        assertThat(saved.getRGB(0, 0) >>> 24).isZero();
    }

    @Test
    @DisplayName("A phone photo should be turned according to EXIF and saved without metadata")
    void shouldApplyExifOrientationAndDropMetadata() throws IOException {
        // Orientation 6 = the camera was turned by 90 degrees: the pixels are stored lying (400x200),
        // and the photo should be shown standing (200x400)
        byte[] phonePhoto = withExifOrientation(image("jpg", 400, 200, BufferedImage.TYPE_INT_RGB), 6);
        Path target = dir.resolve("photo.jpg");

        optimizer.optimizeAndSave(phonePhoto, "jpg", target);

        BufferedImage saved = ImageIO.read(target.toFile());
        assertThat(saved.getWidth()).isEqualTo(200);
        assertThat(saved.getHeight()).isEqualTo(400);
        // No EXIF block in the saved file - there could be e.g. the GPS location of the photo
        assertThat(new String(Files.readAllBytes(target), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }

    @Test
    @DisplayName("A file that only pretends to be an image should be rejected")
    void shouldRejectFileThatIsNotAnImage() {
        byte[] fake = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        Path target = dir.resolve("photo.jpg");

        assertThatThrownBy(() -> optimizer.optimizeAndSave(fake, "jpg", target))
                .isInstanceOfSatisfying(LocalizedException.class,
                        e -> assertThat(e.getCodes()).containsExactly("validation.file.invalidType"));
        assertThat(target).doesNotExist();
    }

    @Test
    @DisplayName("An image with too many pixels should be rejected before decoding")
    void shouldRejectTooManyPixels() throws IOException {
        ImageOptimizer strict = new ImageOptimizer(1600, 0.85f, 1);   // at most 1 megapixel
        byte[] twoMegapixels = image("png", 2000, 1000, BufferedImage.TYPE_INT_RGB);
        Path target = dir.resolve("photo.png");

        assertThatThrownBy(() -> strict.optimizeAndSave(twoMegapixels, "png", target))
                .isInstanceOfSatisfying(LocalizedException.class,
                        e -> assertThat(e.getCodes()).containsExactly("validation.file.dimensionsTooLarge"));
        assertThat(target).doesNotExist();
    }

    /** Creates an image with a gradient and some noise (like a real photo, so that JPEG compression matters). */
    static byte[] image(String format, int width, int height, int type) throws IOException {
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.ORANGE, width, height, Color.BLUE));
        g.fillRect(0, 0, width, height);
        g.dispose();
        Random random = new Random(42);
        for (int i = 0; i < width * height / 20; i++) {
            img.setRGB(random.nextInt(width), random.nextInt(height), random.nextInt(0xFFFFFF) | 0xFF000000);
        }
        if (img.getColorModel().hasAlpha()) {
            // Fully transparent top-left corner - big enough to stay transparent after scaling
            // (a single pixel would be blended with its opaque neighbours)
            for (int x = 0; x < 100; x++) {
                for (int y = 0; y < 100; y++) {
                    img.setRGB(x, y, 0x00000000);
                }
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** Inserts a minimal EXIF block with the given orientation into a JPEG file (right after the JFIF block). */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] app1 = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,              // APP1 marker, block length 34
                'E', 'x', 'i', 'f', 0, 0,                          // EXIF header
                'M', 'M', 0, 0x2A, 0, 0, 0, 0x08,                  // TIFF header (big-endian), first IFD at offset 8
                0, 1,                                              // 1 entry
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,   // tag 0x0112 Orientation, SHORT
                0, 0, 0, 0                                         // no next IFD
        };
        // SOI (2 bytes) + APP0/JFIF block (marker 2 bytes + its length)
        int insertAt = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        byte[] result = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, result, 0, insertAt);
        System.arraycopy(app1, 0, result, insertAt, app1.length);
        System.arraycopy(jpeg, insertAt, result, insertAt + app1.length, jpeg.length - insertAt);
        return result;
    }
}
