package com.helpdesk.observer;

import com.helpdesk.entity.Role;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Observer pattern - Subject side: registration, removal and notification order. */
class TicketEventSubjectTest {

    private final User actor = User.builder().id(1L).fullName("Alice Staff").role(Role.STAFF).build();
    private final Ticket ticket = Ticket.builder().id(10L).ticketRef("TCK-000010").build();
    private final TicketEvent event = TicketEvent.of(TicketEventType.ASSIGNED, ticket, actor);

    @Test
    void notifiesEveryObserverInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        TicketEventSubject subject = new TicketEventSubject(List.of(
                e -> calls.add("first"), e -> calls.add("second"), e -> calls.add("third")));

        subject.notifyObservers(event);

        assertEquals(List.of("first", "second", "third"), calls);
    }

    @Test
    void removedObserverIsNoLongerNotified() {
        List<String> calls = new ArrayList<>();
        TicketObserver observer = e -> calls.add("called");
        TicketEventSubject subject = new TicketEventSubject(List.of());

        subject.addObserver(observer);
        subject.notifyObservers(event);
        subject.removeObserver(observer);
        subject.notifyObservers(event);

        assertEquals(1, calls.size());
    }

    @Test
    void registeringTheSameObserverTwiceNotifiesOnlyOnce() {
        List<String> calls = new ArrayList<>();
        TicketObserver observer = e -> calls.add("called");
        TicketEventSubject subject = new TicketEventSubject(List.of());

        subject.addObserver(observer);
        subject.addObserver(observer);
        subject.notifyObservers(event);

        assertEquals(1, calls.size());
    }

    @Test
    void observerReceivesTheEventItWasSent() {
        List<TicketEvent> received = new ArrayList<>();
        TicketEventSubject subject = new TicketEventSubject(List.of(received::add));

        subject.notifyObservers(event);

        assertEquals(List.of(event), received);
    }
}
