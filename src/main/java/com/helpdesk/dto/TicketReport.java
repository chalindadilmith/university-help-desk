package com.helpdesk.dto;

import java.util.List;
import java.util.Map;

/** Filtered ticket report: volumes, breakdowns, resolution time, satisfaction and per-staff performance. */
public record TicketReport(
        long totalTickets,
        Map<String, Long> countsByStatus,
        Map<String, Long> countsByCategory,
        Map<String, Long> countsByPriority,
        long reopenedOrEscalated,
        long unassigned,
        double averageResolutionHours,
        double averageSatisfactionRating,
        long feedbackCount,
        List<StaffRow> staff
) {
    /** One staff member's workload and results within the selected period. */
    public record StaffRow(String name, long assigned, long resolved, double averageResolutionHours,
                           double averageRating, long ratings) { }
}
