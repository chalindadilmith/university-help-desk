package com.helpdesk.strategy.export;

import com.helpdesk.entity.Ticket;
import com.helpdesk.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Strategy pattern (F5 Reporting) - Context role.
 * Spring injects every ReportExportStrategy bean; the caller only passes the format name and
 * the Context picks the matching strategy at run time.
 */
@Component
public class ReportExporter {

    private final Map<String, ReportExportStrategy> strategies = new LinkedHashMap<>();

    public ReportExporter(List<ReportExportStrategy> available) {
        for (ReportExportStrategy s : available) {
            strategies.put(s.format().toLowerCase(Locale.ROOT), s);
        }
    }

    public ExportedFile export(String format, List<Ticket> tickets) {
        ReportExportStrategy strategy = strategies.get(format == null ? "" : format.toLowerCase(Locale.ROOT));
        if (strategy == null) {
            throw new BusinessRuleException("Unsupported export format '" + format + "'. Available: "
                    + String.join(", ", strategies.keySet()) + ".");
        }
        return strategy.export(tickets);
    }
}
