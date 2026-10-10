package com.helpdesk.service;

import com.helpdesk.dto.ReportSummary;

public interface ReportingService {
    ReportSummary generateSummary();

    /** Tickets created between the two dates (inclusive; either may be null), optionally for one category. */
    com.helpdesk.dto.TicketReport generate(java.time.LocalDate from, java.time.LocalDate to, Long categoryId);

    /** The same filtered tickets as CSV text (one row per ticket). */
    String exportCsv(java.time.LocalDate from, java.time.LocalDate to, Long categoryId);

    /** The same filtered tickets in the requested format ("csv", "json", ...), chosen by an export strategy. */
    com.helpdesk.strategy.export.ExportedFile export(String format, java.time.LocalDate from, java.time.LocalDate to, Long categoryId);
}
