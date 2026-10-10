package com.helpdesk.strategy.export;

import com.helpdesk.entity.Ticket;

import java.util.List;

/**
 * Strategy pattern (F5 Reporting) - Strategy role.
 * One way of turning the filtered ticket list into a downloadable file. To support a new format
 * (PDF, Excel ...) add one new implementation; ReportingServiceImpl does not change.
 */
public interface ReportExportStrategy {

    /** Lower-case key used in the URL, e.g. "csv" or "json". */
    String format();

    ExportedFile export(List<Ticket> tickets);
}
