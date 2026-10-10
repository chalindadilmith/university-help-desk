package com.helpdesk.controller;

import com.helpdesk.entity.TicketAttachment;
import com.helpdesk.service.TicketAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Builds the download response for a ticket attachment (shared by the student and staff controllers). */
final class AttachmentResponses {

    private AttachmentResponses() { }

    private static boolean isImage(String contentType) {
        return contentType.startsWith("image/");
    }

    static ResponseEntity<Resource> file(TicketAttachmentService service, TicketAttachment attachment) {
        Resource resource = service.load(attachment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(service.contentTypeOf(attachment)))
                .header(HttpHeaders.CONTENT_DISPOSITION, (isImage(service.contentTypeOf(attachment))
                        ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(attachment.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }
}
