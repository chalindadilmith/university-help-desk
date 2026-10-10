package com.helpdesk.observer;

import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.User;
import com.helpdesk.service.NotificationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ConcreteObserver: tells Help Desk Staff when something happens that needs them
 * (new ticket in their pool, student reply, ticket released back to the queue,
 * ticket transferred to them, ticket reopened) - UC-F5-01 step 1.
 */
@Component
@Order(2)
public class StaffNotificationObserver implements TicketObserver {

    private static final String ENTITY = "Ticket";

    private final NotificationService notificationService;

    public StaffNotificationObserver(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void update(TicketEvent event) {
        Ticket ticket = event.ticket();
        String ref = ticket.getTicketRef();
        User actor = event.actor();

        switch (event.type()) {
            case CREATED -> {
                JobPool pool = poolOf(ticket);
                if (pool != null) {
                    String message = "New " + ticket.getPriority() + " priority ticket " + ref
                            + " in " + ticket.getCategory().getName() + ".";
                    pool.getStaffMembers().forEach(member -> send(member, "TICKET_NEW", message, ticket));
                }
            }
            case STUDENT_REPLIED -> {
                String message = actor.getFullName() + " replied on ticket " + ref + ".";
                if (ticket.getAssignedStaff() != null) {
                    send(ticket.getAssignedStaff(), "TICKET_REPLY", message, ticket);
                } else {
                    JobPool pool = poolOf(ticket);
                    if (pool != null) {
                        pool.getStaffMembers().forEach(member -> send(member, "TICKET_REPLY", message, ticket));
                    }
                }
            }
            case RELEASED -> {
                JobPool pool = poolOf(ticket);
                if (pool != null) {
                    pool.getStaffMembers().stream()
                            .filter(member -> !member.getId().equals(actor.getId()))
                            .forEach(member -> send(member, "TICKET_NEW",
                                    "Ticket " + ref + " is back in the queue.", ticket));
                }
            }
            case TRANSFERRED -> send(event.target(), "TICKET_ASSIGNED",
                    actor.getFullName() + " transferred ticket " + ref + " to you.", ticket);
            case REOPENED -> {
                if (ticket.getAssignedStaff() != null) {
                    send(ticket.getAssignedStaff(), "TICKET_REOPENED",
                            actor.getFullName() + " reopened ticket " + ref + ".", ticket);
                }
            }
            default -> { /* the other events are for the student, not for staff */ }
        }
    }

    private void send(User recipient, String type, String message, Ticket ticket) {
        notificationService.notify(recipient, type, message, ENTITY, ticket.getId());
    }

    /** The pool that handles this ticket: the one chosen by routing, else the category's default. */
    private static JobPool poolOf(Ticket ticket) {
        return ticket.getRoutedPool() != null ? ticket.getRoutedPool() : ticket.getCategory().getPool();
    }
}
