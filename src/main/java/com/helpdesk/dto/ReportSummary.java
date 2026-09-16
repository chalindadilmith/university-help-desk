package com.helpdesk.dto;

import java.util.Map;

/**
 * UC-F5-01: "compile ticket volume, resolution time, and satisfaction
 * statistics". Computed on demand from live MySQL data via repository
 * queries/streams - nothing here is stored or fabricated.
 */
public record ReportSummary(
        long totalTickets,
        Map<String, Long> countsByStatus,
        double averageResolutionHours,
        double averageSatisfactionRating,
        long feedbackCount
) {
}
