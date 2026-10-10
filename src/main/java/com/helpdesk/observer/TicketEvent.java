package com.helpdesk.observer;

import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.entity.User;

/**
 * The message the Subject pushes to every Observer (the "push" variant of the pattern).
 * It carries the ticket plus just enough context (who did it, who it was handed to,
 * the new status) for an Observer to decide whether, and how, to react.
 *
 * @param type      what happened
 * @param ticket    the ticket it happened to (already updated when the event is published)
 * @param actor     the user who caused it (student or staff)
 * @param target    the other user involved - only set for TRANSFERRED
 * @param newStatus the status that was set - only set for STATUS_CHANGED
 */
public record TicketEvent(TicketEventType type, Ticket ticket, User actor, User target, TicketStatus newStatus) {

    public static TicketEvent of(TicketEventType type, Ticket ticket, User actor) {
        return new TicketEvent(type, ticket, actor, null, null);
    }

    public static TicketEvent statusChanged(Ticket ticket, User actor, TicketStatus newStatus) {
        return new TicketEvent(TicketEventType.STATUS_CHANGED, ticket, actor, null, newStatus);
    }

    public static TicketEvent transferred(Ticket ticket, User actor, User target) {
        return new TicketEvent(TicketEventType.TRANSFERRED, ticket, actor, target, null);
    }
}
