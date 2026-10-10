package com.helpdesk.util;

import com.helpdesk.entity.Priority;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Search, filters, sort and paging for ticket lists. Done in memory on the already
 * access-checked list the service returns, so no extra database queries or access rules
 * are involved.
 *
 * <p>When {@code detailed} is true (Staff and Admin lists) the free-text search also looks at the
 * description, the student's name / e-mail / IDs, the assigned staff member, the priority and the
 * status - i.e. every detail of the ticket.
 */
public final class TicketListView {

    public static final int PAGE_SIZE = 10;

    private final List<Ticket> items;
    private final int page;
    private final int totalPages;
    private final int totalItems;

    private TicketListView(List<Ticket> items, int page, int totalPages, int totalItems) {
        this.items = items;
        this.page = page;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
    }

    /** Backwards compatible entry point (no scope / priority / assignee / category filters). */
    public static TicketListView of(List<Ticket> all, String q, String status, String sort, int page, boolean matchStudent) {
        return of(all, q, status, sort, page, matchStudent, null, null, null, null);
    }

    /**
     * @param q        free text matched against reference, subject and category (and, when {@code detailed},
     *                 every other detail of the ticket)
     * @param status   exact status name, or null/blank for all
     * @param sort     "oldest", "priority" or "status"; anything else means newest first
     * @param page     1-based page number
     * @param scope    "active" (not resolved/closed), "unassigned" (active, nobody handling it),
     *                 "old" (active for more than 3 days) or null/blank for everything
     * @param priority exact priority name, or null/blank for all
     * @param assignee "unassigned", a staff user id, or null/blank for anyone
     * @param categoryId a ticket category id, or null for all
     */
    public static TicketListView of(List<Ticket> all, String q, String status, String sort, int page, boolean detailed,
                                    String scope, String priority, String assignee, Long categoryId) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        TicketStatus wanted = parseStatus(status);
        Priority wantedPriority = parsePriority(priority);
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        String scopeKey = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        String assigneeKey = assignee == null ? "" : assignee.trim();

        List<Ticket> filtered = all.stream()
                .filter(t -> wanted == null || t.getStatus() == wanted)
                .filter(t -> wantedPriority == null || t.getPriority() == wantedPriority)
                .filter(t -> categoryId == null || t.getCategory().getId().equals(categoryId))
                .filter(t -> matchesScope(t, scopeKey, threeDaysAgo))
                .filter(t -> matchesAssignee(t, assigneeKey))
                .filter(t -> needle.isEmpty() || matchesText(t, needle, detailed))
                .toList();

        Comparator<Ticket> order;
        if ("oldest".equals(sort)) {
            order = Comparator.comparing(Ticket::getCreatedAt);
        } else if ("priority".equals(sort)) {
            order = Comparator.comparing((Ticket t) -> t.getPriority().ordinal()).reversed()
                    .thenComparing(Ticket::getCreatedAt, Comparator.reverseOrder());
        } else if ("status".equals(sort)) {
            order = Comparator.comparing((Ticket t) -> t.getStatus().ordinal())
                    .thenComparing(Ticket::getCreatedAt, Comparator.reverseOrder());
        } else {
            order = Comparator.comparing(Ticket::getCreatedAt, Comparator.reverseOrder());
        }
        List<Ticket> sorted = filtered.stream().sorted(order).toList();

        int total = sorted.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 1), pages);
        int from = (current - 1) * PAGE_SIZE;
        List<Ticket> slice = sorted.subList(from, Math.min(total, from + PAGE_SIZE));
        return new TicketListView(slice, current, pages, total);
    }

    // ---------- matching helpers ----------

    private static boolean isActive(Ticket t) {
        return t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED;
    }

    private static boolean matchesScope(Ticket t, String scope, LocalDateTime threeDaysAgo) {
        return switch (scope) {
            case "active" -> isActive(t);
            case "unassigned" -> isActive(t) && t.getAssignedStaff() == null;
            case "old" -> isActive(t) && t.getCreatedAt().isBefore(threeDaysAgo);
            default -> true;
        };
    }

    private static boolean matchesAssignee(Ticket t, String assignee) {
        if (assignee.isEmpty()) {
            return true;
        }
        if ("unassigned".equalsIgnoreCase(assignee)) {
            return t.getAssignedStaff() == null;
        }
        return t.getAssignedStaff() != null && String.valueOf(t.getAssignedStaff().getId()).equals(assignee);
    }

    private static boolean matchesText(Ticket t, String needle, boolean detailed) {
        if (has(t.getTicketRef(), needle) || has(t.getSubject(), needle) || has(t.getCategory().getName(), needle)) {
            return true;
        }
        if (!detailed) {
            return false;
        }
        return has(t.getDescription(), needle)
                || has(t.getPriority().name(), needle)
                || has(t.getStatus().name().replace('_', ' '), needle)
                || has(t.getStudent().getFullName(), needle)
                || has(t.getStudent().getEmail(), needle)
                || has(t.getStudent().getUniversityId(), needle)
                || has(t.getStudent().getStudentCode(), needle)
                || (t.getAssignedStaff() != null && (has(t.getAssignedStaff().getFullName(), needle)
                        || has(t.getAssignedStaff().getEmail(), needle)))
                || (needle.equals("unassigned") && t.getAssignedStaff() == null);
    }

    private static boolean has(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static TicketStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TicketStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Priority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return Priority.valueOf(priority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<Ticket> getItems() { return items; }
    public int getPage() { return page; }
    public int getTotalPages() { return totalPages; }
    public int getTotalItems() { return totalItems; }
    public boolean isHasPrevious() { return page > 1; }
    public boolean isHasNext() { return page < totalPages; }
}
