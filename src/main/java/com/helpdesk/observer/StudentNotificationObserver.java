package com.helpdesk.observer;

import com.helpdesk.entity.Ticket;
import com.helpdesk.service.EmailService;
import com.helpdesk.service.NotificationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ConcreteObserver: keeps the student informed - an in-app notification (the bell icon) plus an
 * e-mail for every change they care about (UC-09 / UC-F2-01 step 9 / F5).
 * Runs last, after the audit entry and the staff notifications.
 */
@Component
@Order(3)
public class StudentNotificationObserver implements TicketObserver {

    private final NotificationService notificationService;
    private final EmailService emailService;

    public StudentNotificationObserver(NotificationService notificationService, EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    @Override
    public void update(TicketEvent event) {
        Ticket ticket = event.ticket();
        String ref = ticket.getTicketRef();

        switch (event.type()) {
            case CREATED -> emailStudent(ticket, "We received your ticket " + ref,
                    "Hello " + ticket.getStudent().getFullName() + ",\n\nYour ticket " + ref + " (\""
                            + ticket.getSubject() + "\") has been received. We will update you when a staff "
                            + "member picks it up.\n\nUniversity Help Desk");
            case ASSIGNED -> notifyStudent(ticket,
                    "Your ticket " + ref + " is now being handled by " + event.actor().getFullName() + ".");
            case STATUS_CHANGED -> notifyStudent(ticket,
                    "Your ticket " + ref + " status changed to " + event.newStatus() + ".");
            case STAFF_REPLIED -> notifyStudent(ticket, "New reply on your ticket " + ref + ".");
            case TRANSFERRED -> notifyStudent(ticket,
                    "Your ticket " + ref + " is now being handled by " + event.target().getFullName() + ".");
            case ESCALATED -> notifyStudent(ticket,
                    "Your ticket " + ref + " has been escalated to senior staff.");
            default -> { /* STUDENT_REPLIED, INTERNAL_NOTE, RELEASED, REOPENED: nothing for the student */ }
        }
    }

    private void notifyStudent(Ticket ticket, String message) {
        notificationService.notify(ticket.getStudent(), "TICKET_STATUS", message, "Ticket", ticket.getId());
        emailStudent(ticket, "Update on your ticket " + ticket.getTicketRef(),
                "Hello " + ticket.getStudent().getFullName() + ",\n\n" + message
                        + "\n\nSign in to the Help Desk to see the details.\n\nUniversity Help Desk");
    }

    private void emailStudent(Ticket ticket, String subject, String body) {
        try {
            emailService.sendAsync(ticket.getStudent().getEmail(), subject, body);
        } catch (RuntimeException ignored) {
            // an email problem must never undo the ticket change
        }
    }
}
