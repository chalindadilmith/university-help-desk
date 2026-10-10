package com.helpdesk.strategy.export;

import com.helpdesk.entity.Ticket;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** ConcreteStrategy: spreadsheet-friendly CSV (the original export logic, moved here unchanged). */
@Component
public class CsvReportExportStrategy implements ReportExportStrategy {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String format() {
        return "csv";
    }

    @Override
    public ExportedFile export(List<Ticket> tickets) {
        StringBuilder sb = new StringBuilder("Reference,Subject,Category,Priority,Status,Student,Assigned staff,Created,Resolved\n");
        for (Ticket t : tickets) {
            sb.append(csv(t.getTicketRef())).append(',')
              .append(csv(t.getSubject())).append(',')
              .append(csv(t.getCategory().getName())).append(',')
              .append(t.getPriority()).append(',')
              .append(t.getStatus()).append(',')
              .append(csv(t.getStudent().getFullName())).append(',')
              .append(csv(t.getAssignedStaff() == null ? "" : t.getAssignedStaff().getFullName())).append(',')
              .append(t.getCreatedAt().format(FMT)).append(',')
              .append(t.getResolvedAt() == null ? "" : t.getResolvedAt().format(FMT)).append('\n');
        }
        // leading byte-order mark so Excel opens it as UTF-8
        byte[] body = ("﻿" + sb).getBytes(StandardCharsets.UTF_8);
        return new ExportedFile("tickets-report.csv", "text/csv; charset=UTF-8", body);
    }

    /** Quotes a CSV field and neutralises spreadsheet formulas (a leading = + - @ would be executed by Excel). */
    private static String csv(String value) {
        String v = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
