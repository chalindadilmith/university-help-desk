package com.helpdesk.observer;

import com.helpdesk.entity.Ticket;
import com.helpdesk.service.AuditLogService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ConcreteObserver: writes the audit-log entry for every staff action on a ticket
 * (UC-02 / F1 "every admin action is written to the audit log").
 * Runs first so the audit trail is recorded before anyone is told about the change.
 */
@Component
@Order(1)
public class AuditLogObserver implements TicketObserver {

    private final AuditLogService auditLogService;

    public AuditLogObserver(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void update(TicketEvent event) {
        Ticket ticket = event.ticket();
        String ref = ticket.getTicketRef();
        switch (event.type()) {
            case ASSIGNED -> log(event, "ASSIGN_TICKET", ref);
            case STATUS_CHANGED -> log(event, "TICKET_STATUS_" + event.newStatus(), ref);
            case INTERNAL_NOTE -> log(event, "TICKET_INTERNAL_NOTE", ref);
            case STAFF_REPLIED -> log(event, "TICKET_REPLY", ref);
            case RELEASED -> log(event, "RELEASE_TICKET", ref);
            case TRANSFERRED -> log(event, "TRANSFER_TICKET", ref + " -> " + event.target().getFullName());
            case ESCALATED -> log(event, "ESCALATE_TICKET", ref);
            default -> { /* CREATED, STUDENT_REPLIED, REOPENED are not audited */ }
        }
    }

    private void log(TicketEvent event, String action, String details) {
        auditLogService.log(event.actor(), action, "Ticket", event.ticket().getId(), details);
    }
}
