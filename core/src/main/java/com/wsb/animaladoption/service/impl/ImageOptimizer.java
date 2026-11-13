package com.wsb.animaladoption.service.impl;

import com.wsb.animaladoption.exception.LocalizedException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Prepares uploaded photos for storage: scales big photos down and saves them again (JPEG with compression).
 * <p>
 * Why:
 * <ul>
 *   <li>a photo straight from a phone has e.g. 4000x3000 px and 3-8 MB, while the page never shows it bigger than
 *       about 1600 px - after scaling the file is usually several times smaller (less disk space, faster pages),</li>
 *   <li>saving the image again drops its metadata (EXIF), which may contain the GPS location where the photo was taken.
 *       The photo is first turned according to the EXIF orientation, so it is not displayed lying on its side,</li>
 *   <li>the file must really decode as an image - a file that only pretends to be one (correct extension and
 *       MIME type, different content) is rejected.</li>
 * </ul>
 * GIF (may be animated - only the first frame would remain) and WEBP (Java cannot write this format)
 * are stored unchanged.
 */
@Slf4j
@Component
public class ImageOptimizer {

    /** Formats that are scaled and saved again; other allowed formats are stored unchanged. */
    private static final Set<String> OPTIMIZED_FORMATS = Set.of("jpg", "png");

    /** Longest side of a saved photo in pixels; smaller photos are never enlarged. */
    private final int maxDimension;
    /** JPEG quality from 0 to 1 - 0.85 looks the same as the original to the eye, and the file is much smaller. */
    private final float jpegQuality;
    /**
     * Decoding takes about 4 bytes of memory per pixel (a 12 MP photo - about 48 MB). A small file can declare
     * huge dimensions (a so-called decompression bomb), so the dimensions are read from the file header
     * and checked BEFORE decoding.
     */
    private final long maxPixels;
    private final int maxMegapixels;

    /** At most 2 photos are decoded at the same time, so that many big uploads at once do not use up the memory. */
    private final Semaphore processingSlots = new Semaphore(2);

    public ImageOptimizer(@Value("${app.upload.image.max-dimension:1600}") int maxDimension,
                          @Value("${app.upload.image.jpeg-quality:0.85}") float jpegQuality,
                          @Value("${app.upload.image.max-megapixels:40}") int maxMegapixels) {
        this.maxDimension = maxDimension;
        this.jpegQuality = jpegQuality;
        this.maxMegapixels = maxMegapixels;
        this.maxPixels = maxMegapixels * 1_000_000L;
    }

    /** Whether files with this extension are optimized (otherwise they are stored unchanged). */
    public boolean supports(String extension) {
        return OPTIMIZED_FORMATS.contains(extension);
    }

    /**
     * Scales the image down if needed and saves it to {@code target} in the given format.
     *
     * @param imageBytes content of the uploaded file
     * @param format     output format - "jpg" or "png" (the extension of the target file)
     * @throws LocalizedException if the file is not a correct image or its dimensions are too big
     * @throws IOException        if the file cannot be written to disk
     */
    public void optimizeAndSave(byte[] imageBytes, String format, Path target) throws IOException {
        ImageSize size = readSize(imageBytes);
        if ((long) size.width() * size.height() > maxPixels) {
            throw new LocalizedException("validation.file.dimensionsTooLarge", maxMegapixels);
        }

        byte[] result;
        processingSlots.acquireUninterruptibly();
        try {
            Thumbnails.Builder<?> image = Thumbnails.of(new ByteArrayInputStream(imageBytes));
            if (Math.max(size.width(), size.height()) > maxDimension) {
                image.size(maxDimension, maxDimension);   // fits into a square, keeping the proportions
            } else {
                image.scale(1.0);                         // a small photo keeps its size - it is never enlarged
            }
            image.outputFormat(format);
            if (format.equals("jpg")) {
                image.outputQuality(jpegQuality);
            }

            // Into memory first: an error here means a damaged image, an error while writing means a disk problem
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            image.toOutputStream(out);
            result = out.toByteArray();
        } catch (IOException | RuntimeException e) {
            // Image decoders throw various exceptions for damaged files (also e.g. ArrayIndexOutOfBoundsException)
            log.warn("Nie udało się przetworzyć obrazu: {}", e.toString());
            throw new LocalizedException("validation.file.invalidType");
        } finally {
            processingSlots.release();
        }

        Files.write(target, result);
        log.debug("Zapisano obraz {}x{} ({} KB) jako {} ({} KB)", size.width(), size.height(),
                imageBytes.length / 1024, target.getFileName(), result.length / 1024);
    }

    /** Reads only the image dimensions from the file header - without decoding the pixels. */
    private ImageSize readSize(byte[] imageBytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new LocalizedException("validation.file.invalidType");   // not an image at all
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new ImageSize(reader.getWidth(0), reader.getHeight(0));
            } catch (IOException | RuntimeException e) {
                throw new LocalizedException("validation.file.invalidType");
            } finally {
                reader.dispose();
            }
        }
    }

    private record ImageSize(int width, int height) {
    }
}
