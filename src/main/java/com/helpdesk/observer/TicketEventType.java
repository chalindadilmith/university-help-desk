package com.helpdesk.observer;

/**
 * Everything that can happen to a ticket that other parts of the system care about.
 * Each value is one kind of "state change" the Subject announces to its Observers.
 */
public enum TicketEventType {
    CREATED,          // student raised a new ticket
    ASSIGNED,         // a staff member took ownership
    STATUS_CHANGED,   // staff moved the ticket to IN_PROGRESS / RESOLVED / CLOSED
    STUDENT_REPLIED,  // student wrote on the ticket
    STAFF_REPLIED,    // staff wrote a public reply
    INTERNAL_NOTE,    // staff wrote a note the student never sees
    RELEASED,         // assigned staff gave the ticket back to the pool
    TRANSFERRED,      // assigned staff handed the ticket to another pool member
    ESCALATED,        // staff escalated to senior staff
    REOPENED          // student reopened a resolved ticket
}
