package com.helpdesk.repository;

import com.helpdesk.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    /** What the student may see: everything except internal staff notes. */
    List<TicketComment> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);
}
