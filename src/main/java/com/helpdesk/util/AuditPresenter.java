package com.helpdesk.util;

import com.helpdesk.entity.AuditLog;

import java.util.Locale;
import java.util.Map;

/**
 * Turns raw audit-log rows into something readable: which area of the system an action belongs to,
 * a plain-English label, and a colour tone. Pure presentation - nothing here touches the database.
 */
public final class AuditPresenter {

    private AuditPresenter() { }

    /** The groups shown as tabs on the audit page. */
    public enum Category {
        TICKETS("Tickets", "fa-ticket"),
        USERS("Users & Accounts", "fa-users-gear"),
        ROUTING("Work Pools & Routing", "fa-route"),
        LOST_FOUND("Lost & Found", "fa-box-open"),
        REGISTRATION("Registration & Modules", "fa-graduation-cap"),
        KNOWLEDGE("Knowledge Base", "fa-book-open"),
        OTHER("Other", "fa-circle-info");

        private final String title;
        private final String icon;

        Category(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }

        public String getTitle() { return title; }
        public String getIcon() { return icon; }
        public String getKey() { return name().toLowerCase(Locale.ROOT); }
    }

    /** One audit row prepared for display. */
    public record Entry(AuditLog log, Category category, String label, String tone) { }

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("CREATE_STAFF_ACCOUNT", "Created a staff account"),
            Map.entry("EDIT_USER", "Edited an account"),
            Map.entry("DEACTIVATE_USER", "Deactivated an account"),
            Map.entry("ACTIVATE_USER", "Reactivated an account"),
            Map.entry("RESET_PASSWORD", "Reset a user's password"),
            Map.entry("CHANGE_PASSWORD", "Changed own password"),
            Map.entry("CREATE_CATEGORY", "Added a ticket category"),
            Map.entry("UPDATE_CATEGORY_POOL", "Changed a category's work pool"),
            Map.entry("CREATE_POOL", "Created a work pool"),
            Map.entry("ASSIGN_STAFF_TO_POOL", "Added staff to a work pool"),
            Map.entry("REMOVE_STAFF_FROM_POOL", "Removed staff from a work pool"),
            Map.entry("CREATE_ROUTING_RULE", "Added a routing rule"),
            Map.entry("UPDATE_ROUTING_RULE", "Changed a routing rule"),
            Map.entry("DELETE_ROUTING_RULE", "Deleted a routing rule"),
            Map.entry("ASSIGN_TICKET", "Took a ticket"),
            Map.entry("RELEASE_TICKET", "Released a ticket to the queue"),
            Map.entry("TRANSFER_TICKET", "Transferred a ticket"),
            Map.entry("ESCALATE_TICKET", "Escalated a ticket"),
            Map.entry("TICKET_REPLY", "Replied on a ticket"),
            Map.entry("TICKET_INTERNAL_NOTE", "Added an internal note"),
            Map.entry("DELETE_TICKET", "Deleted a ticket"),
            Map.entry("EDIT_TICKET", "Student edited their ticket"),
            Map.entry("UPDATE_TICKET", "Edited a ticket"),
            Map.entry("CREATE_TICKET_FOR_STUDENT", "Created a ticket for a student"),
            Map.entry("REPORT_LOST_FOUND_ITEM", "Reported a lost/found item"),
            Map.entry("SUBMIT_CLAIM", "Submitted a claim"),
            Map.entry("RESUBMIT_CLAIM", "Resubmitted a claim"),
            Map.entry("CLAIM_RESUBMITTED", "Resubmitted a claim"),
            Map.entry("VERIFY_CLAIM", "Verified a claim"),
            Map.entry("REJECT_CLAIM", "Rejected a claim"),
            Map.entry("AUTO_REJECT_CLAIM", "Claim auto-rejected"),
            Map.entry("DELETE_CLAIM", "Deleted a claim"),
            Map.entry("DELETE_CLAIM_WITH_REPORT", "Deleted a claim with its report"),
            Map.entry("EDIT_LOST_FOUND_REPORT", "Edited a lost/found report"),
            Map.entry("DELETE_LOST_FOUND_REPORT", "Deleted a lost/found report"),
            Map.entry("ADMIN_REMOVE_LOST_FOUND_REPORT", "Admin removed a lost/found report"),
            Map.entry("CREATE_MODULE", "Added a module"),
            Map.entry("UPDATE_MODULE", "Updated a module"),
            Map.entry("DELETE_MODULE", "Deleted a module"),
            Map.entry("CREATE_OFFERING", "Added a module offering"),
            Map.entry("UPDATE_OFFERING", "Updated a module offering"),
            Map.entry("DELETE_OFFERING", "Deleted a module offering"),
            Map.entry("CREATE_SEMESTER", "Created a semester"),
            Map.entry("OPEN_REGISTRATION", "Opened registration"),
            Map.entry("CLOSE_REGISTRATION", "Closed registration"),
            Map.entry("CREATE_SPECIALISATION", "Added a specialisation"),
            Map.entry("ACTIVATE_SPECIALISATION", "Activated a specialisation"),
            Map.entry("DEACTIVATE_SPECIALISATION", "Deactivated a specialisation"),
            Map.entry("APPROVE_REGISTRATION", "Approved a registration"),
            Map.entry("REJECT_REGISTRATION", "Rejected a registration"));

    public static Entry present(AuditLog log) {
        String action = log.getAction() == null ? "" : log.getAction();
        return new Entry(log, categoryOf(action), labelOf(action), toneOf(action));
    }

    public static Category categoryOf(String action) {
        String a = action.toUpperCase(Locale.ROOT);
        if (a.contains("POOL") || a.contains("CATEGORY") || a.contains("ROUTING")) return Category.ROUTING;
        if (a.contains("TICKET")) return Category.TICKETS;
        if (a.contains("LOST") || a.contains("CLAIM")) return Category.LOST_FOUND;
        if (a.contains("REGISTRATION") || a.contains("SEMESTER") || a.contains("MODULE")
                || a.contains("OFFERING") || a.contains("SPECIALISATION")) return Category.REGISTRATION;
        if (a.contains("KB") || a.contains("ARTICLE") || a.contains("FAQ")) return Category.KNOWLEDGE;
        if (a.contains("USER") || a.contains("STAFF") || a.contains("PASSWORD") || a.contains("ACCOUNT")) return Category.USERS;
        return Category.OTHER;
    }

    public static String labelOf(String action) {
        String known = LABELS.get(action);
        if (known != null) {
            return known;
        }
        if (action.startsWith("TICKET_STATUS_")) {
            String status = action.substring("TICKET_STATUS_".length()).replace('_', ' ').toLowerCase(Locale.ROOT);
            return "Set ticket status to " + status;
        }
        if (action.isBlank()) {
            return "Unknown action";
        }
        String words = action.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** "good" (created/approved), "bad" (deleted/rejected/blocked), "warn" (passwords, escalations) or "info". */
    public static String toneOf(String action) {
        String a = action.toUpperCase(Locale.ROOT);
        if (a.contains("PASSWORD") || a.contains("ESCALATE")) return "warn";
        if (a.startsWith("DELETE") || a.contains("REMOVE") || a.contains("REJECT") || a.startsWith("DEACTIVATE")
                || a.startsWith("CLOSE") || a.startsWith("RELEASE") || a.endsWith("_CLOSED")) return "bad";
        if (a.startsWith("CREATE") || a.startsWith("ADD") || a.startsWith("APPROVE") || a.startsWith("VERIFY")
                || a.startsWith("ACTIVATE") || a.startsWith("ASSIGN") || a.startsWith("OPEN") || a.startsWith("SUBMIT")
                || a.startsWith("REPORT") || a.endsWith("_RESOLVED")) return "good";
        return "info";
    }
}
