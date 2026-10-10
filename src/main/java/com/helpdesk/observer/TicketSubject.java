package com.helpdesk.observer;

/**
 * Subject (lecture role: "Subject"): the operations an object must support to be observed -
 * register an observer, remove one, and notify all of them.
 */
public interface TicketSubject {

    void addObserver(TicketObserver observer);

    void removeObserver(TicketObserver observer);

    void notifyObservers(TicketEvent event);
}
