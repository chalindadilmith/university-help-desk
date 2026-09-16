package com.helpdesk.service.impl;

import com.helpdesk.dto.TicketForm;
import com.helpdesk.entity.*;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.TicketCategoryRepository;
import com.helpdesk.repository.TicketCommentRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.NotificationService;
import com.helpdesk.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketCommentRepository commentRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public TicketServiceImpl(TicketRepository ticketRepository, TicketCategoryRepository categoryRepository,
                              TicketCommentRepository commentRepository, NotificationService notificationService,
                              AuditLogService auditLogService) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public Ticket createTicket(User student, TicketForm form) {
        TicketCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + form.getCategoryId()));

        Ticket ticket = Ticket.builder()
                .student(student)
                .category(category)
                .subject(form.getSubject())
                .description(form.getDescription())
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .ticketRef("PENDING") // placeholder until we know the generated id
                .build();
        Ticket saved = ticketRepository.save(ticket);
        saved.setTicketRef(String.format("TCK-%06d", saved.getId()));
        return saved;
    }

    @Override
    public List<Ticket> myTickets(Long studentId) {
        return ticketRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    @Override
    public List<Ticket> allTickets() {
        return ticketRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Ticket> staffQueue(Long staffId) {
        return ticketRepository.findQueueForStaff(staffId);
    }

    @Override
    public Ticket getById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }

    @Override
    @Transactional
    public Ticket assignToSelf(Long ticketId, User staff) {
        Ticket ticket = getById(ticketId);
        ticket.setAssignedStaff(staff);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        notifyStudent(ticket, "Your ticket " + ticket.getTicketRef() + " is now being handled by " + staff.getFullName() + ".");
        return ticket;
    }

    @Override
    @Transactional
    public Ticket updateStatus(Long ticketId, String newStatus, User staff) {
        Ticket ticket = getById(ticketId);
        TicketStatus status;
        try {
            status = TicketStatus.valueOf(newStatus);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown ticket status: " + newStatus);
        }
        ticket.setStatus(status);
        if (status == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(java.time.LocalDateTime.now());
        }
        notifyStudent(ticket, "Your ticket " + ticket.getTicketRef() + " status changed to " + status + ".");
        return ticket;
    }

    @Override
    @Transactional
    public TicketComment addComment(Long ticketId, User author, String comment, boolean resolutionNote) {
        Ticket ticket = getById(ticketId);
        TicketComment ticketComment = TicketComment.builder()
                .ticket(ticket)
                .author(author)
                .comment(comment)
                .resolutionNote(resolutionNote)
                .build();
        TicketComment saved = commentRepository.save(ticketComment);

        if (author.getRole() != Role.STUDENT) {
            notifyStudent(ticket, "New reply on your ticket " + ticket.getTicketRef() + ".");
        }
        return saved;
    }

    @Override
    @Transactional
    public Ticket escalate(Long ticketId, User staff) {
        Ticket ticket = getById(ticketId);
        ticket.setStatus(TicketStatus.ESCALATED);
        ticket.setPriority(Priority.HIGH);
        auditLogService.log(staff, "ESCALATE_TICKET", "Ticket", ticketId, ticket.getTicketRef());
        notifyStudent(ticket, "Your ticket " + ticket.getTicketRef() + " has been escalated to senior staff.");
        return ticket;
    }

    @Override
    @Transactional
    public Ticket reopen(Long ticketId, User student) {
        Ticket ticket = getById(ticketId);
        ticket.setStatus(TicketStatus.REOPENED);
        if (ticket.getAssignedStaff() != null) {
            notificationService.notify(ticket.getAssignedStaff(), "TICKET_REOPENED",
                    student.getFullName() + " reopened ticket " + ticket.getTicketRef() + ".",
                    "Ticket", ticket.getId());
        }
        return ticket;
    }

    @Override
    public List<TicketComment> commentsFor(Long ticketId) {
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    private void notifyStudent(Ticket ticket, String message) {
        notificationService.notify(ticket.getStudent(), "TICKET_STATUS", message, "Ticket", ticket.getId());
    }
}
