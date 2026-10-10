package com.helpdesk.observer;

import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketCategory;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.entity.User;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.EmailService;
import com.helpdesk.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Observer pattern - the three ConcreteObservers, each tested in isolation with mocked collaborators. */
class TicketObserversTest {

    private User student;
    private User alice;
    private User bob;
    private Ticket ticket;

    private NotificationService notifications;
    private EmailService email;
    private AuditLogService audit;

    @BeforeEach
    void setUp() {
        student = User.builder().id(1L).fullName("Sam Student").email("sam@uni.lk").role(Role.STUDENT).build();
        alice = User.builder().id(2L).fullName("Alice Staff").email("alice@uni.lk").role(Role.STAFF).build();
        bob = User.builder().id(3L).fullName("Bob Staff").email("bob@uni.lk").role(Role.STAFF).build();

        Set<User> members = new HashSet<>(Set.of(alice, bob));
        JobPool pool = JobPool.builder().name("General Support").staffMembers(members).build();
        TicketCategory category = TicketCategory.builder().name("IT").pool(pool).build();
        ticket = Ticket.builder().id(10L).ticketRef("TCK-000010").student(student).category(category)
                .subject("Wi-Fi down").description("details").priority(Priority.MEDIUM).status(TicketStatus.OPEN).build();

        notifications = mock(NotificationService.class);
        email = mock(EmailService.class);
        audit = mock(AuditLogService.class);
    }

    // ---------------- StudentNotificationObserver ----------------

    @Test
    void statusChangeCreatesBellNotificationAndEmailForTheStudent() {
        new StudentNotificationObserver(notifications, email)
                .update(TicketEvent.statusChanged(ticket, alice, TicketStatus.RESOLVED));

        verify(notifications).notify(eq(student), eq("TICKET_STATUS"),
                eq("Your ticket TCK-000010 status changed to RESOLVED."), eq("Ticket"), eq(10L));
        verify(email).sendAsync(eq("sam@uni.lk"), eq("Update on your ticket TCK-000010"), anyString());
    }

    @Test
    void newTicketOnlyEmailsTheStudent() {
        new StudentNotificationObserver(notifications, email)
                .update(TicketEvent.of(TicketEventType.CREATED, ticket, student));

        verify(email).sendAsync(eq("sam@uni.lk"), eq("We received your ticket TCK-000010"), anyString());
        verifyNoInteractions(notifications);
    }

    @Test
    void studentOwnReplyDoesNotNotifyTheStudent() {
        new StudentNotificationObserver(notifications, email)
                .update(TicketEvent.of(TicketEventType.STUDENT_REPLIED, ticket, student));

        verifyNoInteractions(notifications, email);
    }

    @Test
    void emailFailureDoesNotBreakTheObserver() {
        doThrow(new RuntimeException("SMTP down")).when(email).sendAsync(anyString(), anyString(), anyString());

        new StudentNotificationObserver(notifications, email)
                .update(TicketEvent.of(TicketEventType.ESCALATED, ticket, alice)); // must not throw

        verify(notifications).notify(eq(student), eq("TICKET_STATUS"),
                eq("Your ticket TCK-000010 has been escalated to senior staff."), eq("Ticket"), eq(10L));
    }

    // ---------------- StaffNotificationObserver ----------------

    @Test
    void newTicketNotifiesEveryMemberOfThePool() {
        new StaffNotificationObserver(notifications)
                .update(TicketEvent.of(TicketEventType.CREATED, ticket, student));

        String message = "New MEDIUM priority ticket TCK-000010 in IT.";
        verify(notifications).notify(eq(alice), eq("TICKET_NEW"), eq(message), eq("Ticket"), eq(10L));
        verify(notifications).notify(eq(bob), eq("TICKET_NEW"), eq(message), eq("Ticket"), eq(10L));
    }

    @Test
    void studentReplyNotifiesOnlyTheAssignedStaffMember() {
        ticket.setAssignedStaff(alice);

        new StaffNotificationObserver(notifications)
                .update(TicketEvent.of(TicketEventType.STUDENT_REPLIED, ticket, student));

        verify(notifications).notify(eq(alice), eq("TICKET_REPLY"),
                eq("Sam Student replied on ticket TCK-000010."), eq("Ticket"), eq(10L));
        verify(notifications, never()).notify(eq(bob), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void releasedTicketNotifiesTheRestOfThePoolButNotTheStaffMemberWhoReleasedIt() {
        new StaffNotificationObserver(notifications)
                .update(TicketEvent.of(TicketEventType.RELEASED, ticket, alice));

        verify(notifications).notify(eq(bob), eq("TICKET_NEW"),
                eq("Ticket TCK-000010 is back in the queue."), eq("Ticket"), eq(10L));
        verify(notifications, never()).notify(eq(alice), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void transferNotifiesTheNewOwner() {
        new StaffNotificationObserver(notifications)
                .update(TicketEvent.transferred(ticket, alice, bob));

        verify(notifications).notify(eq(bob), eq("TICKET_ASSIGNED"),
                eq("Alice Staff transferred ticket TCK-000010 to you."), eq("Ticket"), eq(10L));
    }

    // ---------------- AuditLogObserver ----------------

    @Test
    void transferIsAuditedWithTheTargetStaffName() {
        new AuditLogObserver(audit).update(TicketEvent.transferred(ticket, alice, bob));

        verify(audit).log(alice, "TRANSFER_TICKET", "Ticket", 10L, "TCK-000010 -> Bob Staff");
    }

    @Test
    void statusChangeIsAuditedWithTheNewStatusInTheActionName() {
        new AuditLogObserver(audit).update(TicketEvent.statusChanged(ticket, alice, TicketStatus.CLOSED));

        verify(audit).log(alice, "TICKET_STATUS_CLOSED", "Ticket", 10L, "TCK-000010");
    }

    @Test
    void newTicketIsNotAudited() {
        new AuditLogObserver(audit).update(TicketEvent.of(TicketEventType.CREATED, ticket, student));

        verify(audit, never()).log(any(), anyString(), anyString(), anyLong(), anyString());
    }
}
