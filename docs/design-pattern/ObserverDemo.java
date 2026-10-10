import java.util.ArrayList;
import java.util.List;

/**
 * SE2030 - Design Patterns: OBSERVER pattern demo for the University Help Desk System.
 *
 * A plain-Java, single-file, runnable version of the exact structure used in the real project
 * (src/main/java/com/helpdesk/observer). No Spring, no database - so it runs anywhere:
 *
 *     java ObserverDemo.java          (Java 21)
 *
 * Lecture role           ->  This demo
 * ----------------------------------------------------------------------
 * Subject (interface)    ->  TicketSubject
 * ConcreteSubject        ->  TicketEventSubject  (keeps the observer list, notifies them)
 * Observer (interface)   ->  TicketObserver      (update(event))
 * ConcreteObserver       ->  StudentNotificationObserver, StaffNotificationObserver,
 *                            AuditLogObserver, SmsObserver (added at run time)
 */
public class ObserverDemo {

    // ---------- the message the subject pushes to its observers ----------
    record TicketEvent(String type, String ticketRef, String actor, String detail) { }

    // ---------- Observer ----------
    interface TicketObserver {
        void update(TicketEvent event);
    }

    // ---------- Subject ----------
    interface TicketSubject {
        void addObserver(TicketObserver observer);
        void removeObserver(TicketObserver observer);
        void notifyObservers(TicketEvent event);
    }

    // ---------- ConcreteSubject ----------
    static class TicketEventSubject implements TicketSubject {
        private final List<TicketObserver> observers = new ArrayList<>();

        @Override public void addObserver(TicketObserver o)    { if (!observers.contains(o)) observers.add(o); }
        @Override public void removeObserver(TicketObserver o) { observers.remove(o); }
        @Override public void notifyObservers(TicketEvent event) {
            for (TicketObserver o : observers) {
                o.update(event);
            }
        }
    }

    // ---------- the "client": plays the role of TicketService ----------
    static class TicketService {
        private final TicketSubject subject;
        private String status = "OPEN";
        private final String ref;

        TicketService(String ref, TicketSubject subject) { this.ref = ref; this.subject = subject; }

        void create(String student)          { subject.notifyObservers(new TicketEvent("CREATED", ref, student, "status=OPEN")); }
        void assign(String staff)            { status = "IN_PROGRESS"; subject.notifyObservers(new TicketEvent("ASSIGNED", ref, staff, "status=" + status)); }
        void changeStatus(String staff, String s) { status = s; subject.notifyObservers(new TicketEvent("STATUS_CHANGED", ref, staff, "status=" + status)); }
        void escalate(String staff)          { status = "ESCALATED"; subject.notifyObservers(new TicketEvent("ESCALATED", ref, staff, "status=" + status)); }
    }

    // ---------- ConcreteObservers ----------
    static class AuditLogObserver implements TicketObserver {
        @Override public void update(TicketEvent e) {
            if (e.type().equals("CREATED")) return;   // same rule as the real AuditLogObserver
            System.out.printf("   [AUDIT   ] %-14s %s by %s%n", e.type(), e.ticketRef(), e.actor());
        }
    }

    static class StaffNotificationObserver implements TicketObserver {
        @Override public void update(TicketEvent e) {
            if (e.type().equals("CREATED")) {
                System.out.printf("   [STAFF   ] Pool notified: new ticket %s is waiting in the queue%n", e.ticketRef());
            }
        }
    }

    static class StudentNotificationObserver implements TicketObserver {
        @Override public void update(TicketEvent e) {
            switch (e.type()) {
                case "ASSIGNED"       -> send("Your ticket " + e.ticketRef() + " is now being handled by " + e.actor() + ".");
                case "STATUS_CHANGED" -> send("Your ticket " + e.ticketRef() + " status changed (" + e.detail() + ").");
                case "ESCALATED"      -> send("Your ticket " + e.ticketRef() + " has been escalated to senior staff.");
                default -> { }
            }
        }
        private void send(String message) {
            System.out.println("   [STUDENT ] bell + e-mail -> " + message);
        }
    }

    /** A brand-new reaction, written without touching TicketService or any other observer. */
    static class SmsObserver implements TicketObserver {
        @Override public void update(TicketEvent e) {
            if (e.type().equals("ESCALATED")) {
                System.out.println("   [SMS     ] text message sent: ticket " + e.ticketRef() + " escalated");
            }
        }
    }

    // ---------- demo ----------
    public static void main(String[] args) {
        TicketEventSubject subject = new TicketEventSubject();
        TicketObserver audit = new AuditLogObserver();
        TicketObserver staff = new StaffNotificationObserver();
        TicketObserver student = new StudentNotificationObserver();

        // registration order = notification order (the real project fixes this with @Order)
        subject.addObserver(audit);
        subject.addObserver(staff);
        subject.addObserver(student);

        TicketService ticket = new TicketService("TCK-000042", subject);

        System.out.println("1) Student raises a ticket");
        ticket.create("Sam Student");

        System.out.println("\n2) Staff member takes the ticket");
        ticket.assign("Alice Staff");

        System.out.println("\n3) Staff resolves it");
        ticket.changeStatus("Alice Staff", "RESOLVED");

        System.out.println("\n4) We add an SMS observer at run time - TicketService is NOT changed");
        subject.addObserver(new SmsObserver());
        ticket.escalate("Alice Staff");

        System.out.println("\n5) We remove the student observer at run time (like 'unfollow')");
        subject.removeObserver(student);
        ticket.changeStatus("Alice Staff", "CLOSED");
        System.out.println("   (no [STUDENT ] line above: that observer is no longer subscribed)");
    }
}
