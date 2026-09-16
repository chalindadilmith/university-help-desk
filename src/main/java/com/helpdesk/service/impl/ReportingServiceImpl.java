package com.helpdesk.service.impl;

import com.helpdesk.dto.ReportSummary;
import com.helpdesk.entity.Feedback;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.repository.FeedbackRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.service.ReportingService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportingServiceImpl implements ReportingService {

    private final TicketRepository ticketRepository;
    private final FeedbackRepository feedbackRepository;

    public ReportingServiceImpl(TicketRepository ticketRepository, FeedbackRepository feedbackRepository) {
        this.ticketRepository = ticketRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    public ReportSummary generateSummary() {
        List<Ticket> allTickets = ticketRepository.findAll();

        Map<String, Long> countsByStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            countsByStatus.put(status.name(), ticketRepository.countByStatus(status));
        }

        double avgResolutionHours = allTickets.stream()
                .filter(t -> t.getResolvedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes())
                .average()
                .orElse(0) / 60.0;

        List<Feedback> feedbackList = feedbackRepository.findAll();
        double avgRating = feedbackList.stream()
                .mapToInt(Feedback::getRating)
                .average()
                .orElse(0);

        return new ReportSummary(allTickets.size(), countsByStatus, avgResolutionHours, avgRating, feedbackList.size());
    }
}
