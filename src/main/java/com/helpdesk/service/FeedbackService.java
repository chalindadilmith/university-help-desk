package com.helpdesk.service;

import com.helpdesk.entity.Feedback;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.User;

import java.util.Optional;

public interface FeedbackService {

    /** @throws com.helpdesk.exception.BusinessRuleException if the ticket isn't Resolved/Closed or already has feedback. */
    Feedback submitFeedback(Ticket ticket, User student, int rating, String comment);

    Optional<Feedback> findByTicketId(Long ticketId);
}
