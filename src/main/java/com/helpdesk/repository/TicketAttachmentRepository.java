package com.helpdesk.repository;

import com.helpdesk.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    List<TicketAttachment> findByTicketId(Long ticketId);

    java.util.Optional<TicketAttachment> findByIdAndTicketId(Long id, Long ticketId);
}
