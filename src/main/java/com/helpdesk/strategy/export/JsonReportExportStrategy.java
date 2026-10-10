package com.helpdesk.strategy.export;

import com.helpdesk.entity.Ticket;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** ConcreteStrategy: JSON array, handy for other tools. Built by hand so no entity is serialised by accident. */
@Component
public class JsonReportExportStrategy implements ReportExportStrategy {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String format() {
        return "json";
    }

    @Override
    public ExportedFile export(List<Ticket> tickets) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            sb.append("  {")
              .append(field("reference", t.getTicketRef())).append(", ")
              .append(field("subject", t.getSubject())).append(", ")
              .append(field("category", t.getCategory().getName())).append(", ")
              .append(field("priority", String.valueOf(t.getPriority()))).append(", ")
              .append(field("status", String.valueOf(t.getStatus()))).append(", ")
              .append(field("student", t.getStudent().getFullName())).append(", ")
              .append(field("assignedStaff", t.getAssignedStaff() == null ? null : t.getAssignedStaff().getFullName())).append(", ")
              .append(field("created", t.getCreatedAt().format(FMT))).append(", ")
              .append(field("resolved", t.getResolvedAt() == null ? null : t.getResolvedAt().format(FMT)))
              .append('}').append(i < tickets.size() - 1 ? "," : "").append('\n');
        }
        sb.append("]\n");
        return new ExportedFile("tickets-report.json", "application/json; charset=UTF-8",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String field(String name, String value) {
        return "\"" + name + "\": " + (value == null ? "null" : "\"" + escape(value) + "\"");
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
