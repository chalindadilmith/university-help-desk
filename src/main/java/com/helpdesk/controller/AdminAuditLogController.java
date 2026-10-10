package com.helpdesk.controller;

import com.helpdesk.entity.AuditLog;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.util.AuditPresenter;
import com.helpdesk.util.AuditPresenter.Category;
import com.helpdesk.util.AuditPresenter.Entry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Audit page: the latest 500 administrative actions, grouped by area (tabs) and by day,
 * searchable, each with a readable label instead of the raw action code.
 */
@Controller
@RequestMapping("/admin/audit-log")
public class AdminAuditLogController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH);

    /** One day's worth of entries. */
    public record DayGroup(String label, List<Entry> entries) { }

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String view(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       Model model) {
        List<AuditLog> logs = auditLogService.recentLogsExtended();
        List<Entry> all = logs.stream().map(AuditPresenter::present).toList();

        Map<Category, Long> counts = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            counts.put(c, all.stream().filter(e -> e.category() == c).count());
        }

        Category selected = null;
        if (category != null && !category.isBlank()) {
            for (Category c : Category.values()) {
                if (c.getKey().equalsIgnoreCase(category)) {
                    selected = c;
                }
            }
        }
        final Category wanted = selected;
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<Entry> filtered = all.stream()
                .filter(e -> wanted == null || e.category() == wanted)
                .filter(e -> needle.isEmpty() || matches(e, needle))
                .toList();

        LocalDate today = LocalDate.now();
        Map<LocalDate, List<Entry>> byDay = new LinkedHashMap<>();
        for (Entry e : filtered) {
            byDay.computeIfAbsent(e.log().getCreatedAt().toLocalDate(), d -> new ArrayList<>()).add(e);
        }
        List<DayGroup> groups = new ArrayList<>();
        byDay.forEach((day, entries) -> {
            String label = day.equals(today) ? "Today" : day.equals(today.minusDays(1)) ? "Yesterday" : day.format(DAY);
            groups.add(new DayGroup(label + " · " + entries.size() + (entries.size() == 1 ? " action" : " actions"), entries));
        });

        model.addAttribute("groups", groups);
        model.addAttribute("counts", counts);
        model.addAttribute("totalAll", all.size());
        model.addAttribute("totalShown", filtered.size());
        model.addAttribute("categories", Category.values());
        model.addAttribute("selected", wanted);
        model.addAttribute("q", q);
        return "admin/audit-log";
    }

    private static boolean matches(Entry e, String needle) {
        AuditLog log = e.log();
        return has(e.label(), needle) || has(log.getAction(), needle) || has(log.getDetails(), needle)
                || has(log.getTargetEntity(), needle) || has(log.getActor().getFullName(), needle)
                || has(log.getActor().getEmail(), needle);
    }

    private static boolean has(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
