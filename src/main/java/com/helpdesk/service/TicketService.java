package com.helpdesk.service;

import com.helpdesk.dto.TicketForm;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketComment;
import com.helpdesk.entity.User;

import java.util.List;

public interface TicketService {

    Ticket createTicket(User student, TicketForm form);

    List<Ticket> myTickets(Long studentId);

    List<Ticket> allTickets();

    List<Ticket> staffQueue(Long staffId);

    Ticket getById(Long id);

    Ticket assignToSelf(Long ticketId, User staff);

    Ticket updateStatus(Long ticketId, String newStatus, User staff);

    TicketComment addComment(Long ticketId, User author, String comment, boolean resolutionNote);

    Ticket escalate(Long ticketId, User staff);

    /** Student reopens a ticket they're not satisfied with (UC-F2-01 extension 8a / UC-09 extension 10a). */
    Ticket reopen(Long ticketId, User student);

    List<TicketComment> commentsFor(Long ticketId);
}
