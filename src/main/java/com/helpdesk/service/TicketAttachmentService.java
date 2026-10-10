package com.helpdesk.service;

import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketAttachment;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.TicketAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Files a student attaches to a ticket (screenshots, PDFs). Only PNG, JPG and PDF up to 5 MB
 * are accepted (at most 3 per ticket), the content must match the extension, files are stored
 * under a server-generated name, and they are only served back through controller endpoints
 * that check the viewer is allowed to see the ticket.
 */
@Service
public class TicketAttachmentService {

    public static final int MAX_FILES = 3;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "pdf", "application/pdf");

    private final Path root;
    private final TicketAttachmentRepository repository;

    public TicketAttachmentService(@Value("${app.upload-dir:uploads}") String uploadDir,
                                    TicketAttachmentRepository repository) {
        this.root = Paths.get(uploadDir, "ticket-attachments").toAbsolutePath().normalize();
        this.repository = repository;
    }

    /** Checks every non-empty file; throws BusinessRuleException with a student-friendly message. */
    public void validate(MultipartFile[] files) {
        List<MultipartFile> real = nonEmpty(files);
        if (real.size() > MAX_FILES) {
            throw new BusinessRuleException("You can attach at most " + MAX_FILES + " files to a ticket.");
        }
        for (MultipartFile f : real) {
            String ext = extensionOf(f.getOriginalFilename());
            if (!TYPES.containsKey(ext)) {
                throw new BusinessRuleException("Attachments must be PNG, JPG or PDF files.");
            }
            if (f.getSize() > MAX_BYTES) {
                throw new BusinessRuleException("Each attachment must be 5 MB or smaller.");
            }
            if (!contentMatches(f, ext)) {
                throw new BusinessRuleException("\"" + displayName(f.getOriginalFilename())
                        + "\" does not look like a real " + ext.toUpperCase(Locale.ROOT) + " file.");
            }
        }
    }

    @Transactional
    public void storeAll(Ticket ticket, MultipartFile[] files) {
        List<MultipartFile> real = nonEmpty(files);
        validate(files);
        try {
            Files.createDirectories(root);
            for (MultipartFile f : real) {
                String ext = extensionOf(f.getOriginalFilename());
                String storedName = UUID.randomUUID() + "." + ext;
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, root.resolve(storedName));
                }
                repository.save(TicketAttachment.builder()
                        .ticket(ticket)
                        .fileName(displayName(f.getOriginalFilename()))
                        .filePath(storedName)
                        .build());
            }
        } catch (IOException e) {
            throw new BusinessRuleException("An attachment could not be saved. Please try again.");
        }
    }

    /** Removes every attachment record of a ticket and its stored files (used when the ticket is deleted). */
    @Transactional
    public void deleteAllForTicket(Long ticketId) {
        List<TicketAttachment> attachments = repository.findByTicketId(ticketId);
        for (TicketAttachment attachment : attachments) {
            try {
                Path path = root.resolve(attachment.getFilePath()).normalize();
                if (path.startsWith(root)) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException ignored) {
                // an orphaned file on disk must never block deleting the ticket
            }
        }
        repository.deleteAll(attachments);
    }

    public List<TicketAttachment> forTicket(Long ticketId) {
        return repository.findByTicketId(ticketId);
    }

    public TicketAttachment get(Long ticketId, Long attachmentId) {
        return repository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found."));
    }

    public Resource load(TicketAttachment attachment) {
        Path path = root.resolve(attachment.getFilePath()).normalize();
        if (!path.startsWith(root) || !Files.isReadable(path)) {
            throw new ResourceNotFoundException("Attachment file not found.");
        }
        return new FileSystemResource(path);
    }

    public String contentTypeOf(TicketAttachment attachment) {
        return TYPES.getOrDefault(extensionOf(attachment.getFilePath()), "application/octet-stream");
    }

    // ---------- helpers ----------

    private static List<MultipartFile> nonEmpty(MultipartFile[] files) {
        if (files == null) {
            return List.of();
        }
        return java.util.Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).toList();
    }

    private static boolean contentMatches(MultipartFile file, String ext) {
        try (InputStream in = file.getInputStream()) {
            byte[] h = in.readNBytes(5);
            if (ext.equals("png")) {
                return h.length >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            }
            if (ext.equals("pdf")) {
                return h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-';
            }
            return h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        } catch (IOException e) {
            return false;
        }
    }

    /** File name as shown to users: no path, no control characters, at most 120 characters. */
    private static String displayName(String original) {
        String name = original == null ? "file" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    private static String extensionOf(String name) {
        if (name == null || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
