package com.helpdesk.service;

import com.helpdesk.dto.TicketForm;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketComment;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.entity.User;

import com.helpdesk.dto.ManagedTicketForm;
import com.helpdesk.dto.TicketEditForm;

import java.util.List;

public interface TicketService {

    Ticket createTicket(User student, TicketForm form);

    List<Ticket> myTickets(Long studentId);

    List<Ticket> allTickets();

    List<Ticket> staffQueue(Long staffId);

    Ticket getById(Long id);

    /**
     * Loads a ticket for a staff member. Only the assigned staff member, or (while it is
     * unassigned) a member of the work-pool that handles the ticket's category, may open it.
     * Anyone else gets an AccessDeniedException.
     */
    Ticket getForStaff(Long id, User staff);

    /** Staff takes ownership. Only a pool member, and only while the ticket is unassigned. */
    Ticket assignToSelf(Long ticketId, User staff);

    /** Staff changes status. Only the assigned staff member, and only along an allowed transition. */
    Ticket updateStatus(Long ticketId, String newStatus, User staff);

    /** Staff or student reply. Staff must be the assigned member; nobody can reply on a closed ticket. */
    default TicketComment addComment(Long ticketId, User author, String comment, boolean resolutionNote) {
        return addComment(ticketId, author, comment, resolutionNote, false);
    }

    /** As above; {@code internal} marks a staff-only note that the student never sees. */
    TicketComment addComment(Long ticketId, User author, String comment, boolean resolutionNote, boolean internal);

    /** Assigned staff gives the ticket back to its pool (unassigned). */
    Ticket release(Long ticketId, User staff);

    /** Assigned staff hands the ticket to another member of the same pool. */
    Ticket transfer(Long ticketId, User staff, Long targetStaffId);

    /** Members of the ticket's pool with how many unfinished tickets each currently holds. */
    List<StaffLoad> poolWorkload(Ticket ticket);

    record StaffLoad(User staff, long active) { }

    /** Comments the student may see (internal staff notes removed). */
    List<TicketComment> publicCommentsFor(Long ticketId);

    /** Staff escalates. Only the assigned staff member, and only an active ticket that is not already escalated. */
    Ticket escalate(Long ticketId, User staff);

    /** Student reopens a ticket they're not satisfied with. Only allowed while the ticket is Resolved. */
    Ticket reopen(Long ticketId, User student);

    List<TicketComment> commentsFor(Long ticketId);

    /**
     * Permanently deletes a ticket with its replies, attachments, feedback and notifications.
     * A student may delete only their own ticket (for example one submitted by mistake);
     * Staff and Admin may delete any ticket. The deletion is written to the audit log and,
     * when someone other than the student deletes it, the student is notified.
     */
    void deleteTicket(Long ticketId, User actor);

    /** Student corrects their own ticket. Not allowed once it is Resolved or Closed. */
    Ticket updateOwnTicket(Long ticketId, User student, TicketEditForm form);

    /** Admin / Staff create a ticket on behalf of a student. */
    Ticket createForStudent(ManagedTicketForm form, User actor);

    /**
     * Admin / Staff edit any ticket. Only an Admin can change who it is assigned to;
     * for Staff the assignee field is ignored.
     */
    Ticket updateManaged(Long ticketId, ManagedTicketForm form, User actor);

    /** True when this staff member could take the (unassigned) ticket, i.e. belongs to its work-pool. */
    boolean canTake(Ticket ticket, User staff);

    /** Statuses staff may move this ticket to next (drives the status drop-down). */
    List<TicketStatus> nextStatuses(Ticket ticket);

    /** Whether the ticket can still be escalated. */
    boolean canEscalate(Ticket ticket);
}
