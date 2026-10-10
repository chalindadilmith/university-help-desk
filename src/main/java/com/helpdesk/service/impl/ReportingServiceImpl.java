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
    private final com.helpdesk.strategy.export.ReportExporter reportExporter;

    public ReportingServiceImpl(TicketRepository ticketRepository, FeedbackRepository feedbackRepository,
                                com.helpdesk.strategy.export.ReportExporter reportExporter) {
        this.reportExporter = reportExporter;
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

    // ---------- filtered report ----------

    private List<Ticket> filtered(java.time.LocalDate from, java.time.LocalDate to, Long categoryId) {
        java.time.LocalDateTime start = from == null ? null : from.atStartOfDay();
        java.time.LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
        return ticketRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> start == null || !t.getCreatedAt().isBefore(start))
                .filter(t -> end == null || t.getCreatedAt().isBefore(end))
                .filter(t -> categoryId == null || t.getCategory().getId().equals(categoryId))
                .toList();
    }

    private static double avgHours(List<Ticket> tickets) {
        return tickets.stream()
                .filter(t -> t.getResolvedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes())
                .average().orElse(0) / 60.0;
    }

    @Override
    public com.helpdesk.dto.TicketReport generate(java.time.LocalDate from, java.time.LocalDate to, Long categoryId) {
        List<Ticket> tickets = filtered(from, to, categoryId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            byStatus.put(s.name(), tickets.stream().filter(t -> t.getStatus() == s).count());
        }
        Map<String, Long> byCategory = new java.util.TreeMap<>();
        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (com.helpdesk.entity.Priority p : com.helpdesk.entity.Priority.values()) {
            byPriority.put(p.name(), tickets.stream().filter(t -> t.getPriority() == p).count());
        }
        for (Ticket t : tickets) {
            byCategory.merge(t.getCategory().getName(), 1L, Long::sum);
        }

        java.util.Set<Long> ids = tickets.stream().map(Ticket::getId).collect(java.util.stream.Collectors.toSet());
        List<Feedback> feedback = feedbackRepository.findAll().stream()
                .filter(f -> ids.contains(f.getTicket().getId())).toList();
        double avgRating = feedback.stream().mapToInt(Feedback::getRating).average().orElse(0);

        Map<Long, List<Ticket>> byStaff = tickets.stream().filter(t -> t.getAssignedStaff() != null)
                .collect(java.util.stream.Collectors.groupingBy(t -> t.getAssignedStaff().getId()));
        List<com.helpdesk.dto.TicketReport.StaffRow> rows = new java.util.ArrayList<>();
        for (List<Ticket> mine : byStaff.values()) {
            com.helpdesk.entity.User staff = mine.get(0).getAssignedStaff();
            List<Feedback> theirs = feedback.stream()
                    .filter(f -> f.getStaff().getId().equals(staff.getId())).toList();
            rows.add(new com.helpdesk.dto.TicketReport.StaffRow(
                    staff.getFullName(),
                    mine.size(),
                    mine.stream().filter(t -> t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED).count(),
                    avgHours(mine),
                    theirs.stream().mapToInt(Feedback::getRating).average().orElse(0),
                    theirs.size()));
        }
        rows.sort(java.util.Comparator.comparing(com.helpdesk.dto.TicketReport.StaffRow::name));

        return new com.helpdesk.dto.TicketReport(
                tickets.size(), byStatus, byCategory, byPriority,
                tickets.stream().filter(t -> t.getStatus() == TicketStatus.REOPENED || t.getStatus() == TicketStatus.ESCALATED).count(),
                tickets.stream().filter(t -> t.getAssignedStaff() == null
                        && t.getStatus() != TicketStatus.CLOSED && t.getStatus() != TicketStatus.RESOLVED).count(),
                avgHours(tickets), avgRating, feedback.size(), rows);
    }

    @Override
    public com.helpdesk.strategy.export.ExportedFile export(String format, java.time.LocalDate from,
                                                            java.time.LocalDate to, Long categoryId) {
        // Strategy pattern: the exporter picks the CSV / JSON / ... strategy by format name
        return reportExporter.export(format, filtered(from, to, categoryId));
    }

    @Override
    public String exportCsv(java.time.LocalDate from, java.time.LocalDate to, Long categoryId) {
        String text = new String(export("csv", from, to, categoryId).body(), java.nio.charset.StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text; // callers add their own BOM
    }
}
