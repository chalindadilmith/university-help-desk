package com.helpdesk.service.impl;

import com.helpdesk.entity.Feedback;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.entity.User;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.repository.FeedbackRepository;
import com.helpdesk.service.FeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    @Transactional
    public Feedback submitFeedback(Ticket ticket, User student, int rating, String comment) {
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new BusinessRuleException("Feedback can only be submitted once a ticket is Resolved or Closed.");
        }
        if (feedbackRepository.findByTicketId(ticket.getId()).isPresent()) {
            throw new BusinessRuleException("Feedback has already been submitted for this ticket.");
        }
        if (ticket.getAssignedStaff() == null) {
            throw new BusinessRuleException("This ticket has no assigned staff member to rate.");
        }

        Feedback feedback = Feedback.builder()
                .ticket(ticket)
                .student(student)
                .staff(ticket.getAssignedStaff())
                .rating(rating)
                .comment(comment)
                .build();
        return feedbackRepository.save(feedback);
    }

    @Override
    public Optional<Feedback> findByTicketId(Long ticketId) {
        return feedbackRepository.findByTicketId(ticketId);
    }

    public List<Feedback> all() {
        return feedbackRepository.findAll();
    }
}
