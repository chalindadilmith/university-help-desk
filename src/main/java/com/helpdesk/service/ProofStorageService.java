package com.helpdesk.service;

import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores the optional proof-of-ownership file attached to a Lost &amp; Found claim.
 * Only images and PDFs up to 5 MB are accepted, files are saved under a server-generated
 * name (the client's file name is never used as a path), and they are only served back
 * through the admin-only controller endpoint.
 */
@Service
public class ProofStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Map<String, String> TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "pdf", "application/pdf");

    private final Path root;

    public ProofStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir, "lost-found-proofs").toAbsolutePath().normalize();
    }

    /** Validates and stores the file; returns the generated stored name. */
    public String store(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("The proof file must be 5 MB or smaller.");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!TYPES.containsKey(ext)) {
            throw new BusinessRuleException("The proof file must be a PNG, JPG or PDF.");
        }
        String storedName = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(root);
            Files.copy(file.getInputStream(), root.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessRuleException("The proof file could not be saved. Please try again.");
        }
        return storedName;
    }

    /** Photo upload (reporter's item photo or claimant's evidence): PNG/JPG only, max 5 MB, content checked. */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please upload a photo.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("The photo must be 5 MB or smaller.");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg")) {
            throw new BusinessRuleException("The photo must be a PNG or JPG image.");
        }
        try (java.io.InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            boolean png = head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            boolean jpg = head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            if (!png && !jpg) {
                throw new BusinessRuleException("That file is not a valid PNG or JPG image.");
            }
        } catch (IOException e) {
            throw new BusinessRuleException("The photo could not be read. Please try again.");
        }
        verifyRealImage(file, ext);
        return store(file);
    }

    /**
     * Checks the file really decodes as an image of the type its extension claims, and is not
     * absurdly large (a tiny file can describe a huge image that exhausts server memory).
     * The decode uses sub-sampling so even a large photo needs little memory.
     */
    private static void verifyRealImage(MultipartFile file, String ext) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new BusinessRuleException("That file is not a valid PNG or JPG image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    throw new BusinessRuleException("The photo is too large. Please use one under 40 megapixels.");
                }
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean matches = ext.equals("png") ? format.equals("png") : format.equals("jpeg") || format.equals("jpg");
                if (!matches) {
                    throw new BusinessRuleException("The file type does not match its extension. Please upload a real PNG or JPG.");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(8, 8, 0, 0);
                reader.read(0, param); // throws if the image data is corrupt
            } finally {
                reader.dispose();
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessRuleException("That file is not a valid PNG or JPG image.");
        }
    }

    public Resource load(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || !Files.isReadable(path)) {
            throw new ResourceNotFoundException("Proof file not found.");
        }
        return new FileSystemResource(path);
    }

    /** Best-effort removal of a stored photo (used when a report or claim is deleted). Never throws. */
    public void delete(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Path path = root.resolve(storedName).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException ignored) {
            // a leftover file is harmless; the database record is what matters
        }
    }

    public String contentTypeOf(String storedName) {
        return TYPES.getOrDefault(extensionOf(storedName), "application/octet-stream");
    }

    private static String extensionOf(String name) {
        if (name == null || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
