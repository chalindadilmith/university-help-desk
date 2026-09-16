package com.helpdesk.repository;

import com.helpdesk.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByTicketId(Long ticketId);
    List<Feedback> findByStaffId(Long staffId);
    List<Feedback> findAll();
}
