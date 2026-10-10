package com.helpdesk.observer;

/**
 * Observer (lecture role: "Observer"): the contract every dependent of a ticket implements.
 * The subject calls update() on each registered observer whenever a ticket changes.
 */
public interface TicketObserver {

    void update(TicketEvent event);
}
