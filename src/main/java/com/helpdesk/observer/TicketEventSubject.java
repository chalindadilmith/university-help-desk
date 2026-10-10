package com.helpdesk.observer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ConcreteSubject (lecture role: "ConcreteSubject", like TsunamiWarningSystem in the slides).
 * It keeps the list of observers and, when TicketService reports a change, calls update() on
 * each of them.
 *
 * Spring injects every {@link TicketObserver} bean into the constructor, already sorted by
 * their @Order values. That fixes the weak point the lecture lists for this pattern
 * ("observers are updated in a random sequence"): here the order is explicit and repeatable.
 *
 * Observers run synchronously, inside the caller's transaction. An in-app notification or audit
 * row is therefore committed or rolled back together with the ticket change - exactly as before
 * the refactoring, when TicketServiceImpl called those services directly.
 */
@Component
public class TicketEventSubject implements TicketSubject {

    private final CopyOnWriteArrayList<TicketObserver> observers = new CopyOnWriteArrayList<>();

    public TicketEventSubject(List<TicketObserver> registeredObservers) {
        registeredObservers.forEach(this::addObserver);
    }

    @Override
    public void addObserver(TicketObserver observer) {
        if (observer != null) {
            observers.addIfAbsent(observer);
        }
    }

    @Override
    public void removeObserver(TicketObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(TicketEvent event) {
        for (TicketObserver observer : observers) {
            observer.update(event);
        }
    }
}
