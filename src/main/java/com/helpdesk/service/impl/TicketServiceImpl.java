package com.helpdesk.service.impl;

import com.helpdesk.dto.ManagedTicketForm;
import com.helpdesk.dto.TicketEditForm;
import com.helpdesk.dto.TicketForm;
import com.helpdesk.entity.*;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.FeedbackRepository;
import com.helpdesk.repository.NotificationRepository;
import com.helpdesk.repository.TicketCategoryRepository;
import com.helpdesk.repository.TicketCommentRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.repository.RoutingRuleRepository;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.observer.TicketEvent;
import com.helpdesk.observer.TicketEventType;
import com.helpdesk.observer.TicketSubject;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.NotificationService;
import com.helpdesk.service.TicketAttachmentService;
import com.helpdesk.service.TicketService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TicketServiceImpl implements TicketService {

    /** Statuses a staff member may set through the status drop-down. */
    private static final Set<TicketStatus> STAFF_SETTABLE =
            EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED);

    /** Allowed status changes. Closed is final; Resolved can only be closed (a student reopens it). */
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.REOPENED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.ESCALATED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED),
            TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));

    private static final Set<TicketStatus> ESCALATABLE =
            EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.REOPENED);

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketCommentRepository commentRepository;
    /** Strategy pattern: decides which work-pool a ticket is routed to (rule-based, then category default). */
    private final com.helpdesk.strategy.routing.TicketRouter ticketRouter;
    private final UserRepository userRepository;
    /**
     * Observer pattern: instead of calling the notification, e-mail and audit services itself,
     * this service just announces what happened. Every registered TicketObserver reacts on its own.
     */
    private final TicketSubject ticketEvents;
    private final FeedbackRepository feedbackRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final TicketAttachmentService attachmentService;
    private final AuditLogService auditLogService;

    public TicketServiceImpl(TicketRepository ticketRepository, TicketCategoryRepository categoryRepository,
                              TicketCommentRepository commentRepository, com.helpdesk.strategy.routing.TicketRouter ticketRouter,
                              UserRepository userRepository, TicketSubject ticketEvents,
                              FeedbackRepository feedbackRepository, NotificationRepository notificationRepository,
                              NotificationService notificationService, TicketAttachmentService attachmentService,
                              AuditLogService auditLogService) {
        this.feedbackRepository = feedbackRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.attachmentService = attachmentService;
        this.auditLogService = auditLogService;
        this.ticketRouter = ticketRouter;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
        this.ticketEvents = ticketEvents;
    }

    @Override
    @Transactional
    public Ticket createTicket(User student, TicketForm form) {
        TicketCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + form.getCategoryId()));

        Priority priority = form.getPriority() == null ? Priority.MEDIUM : form.getPriority();
        // Strategy pattern: the router tries the routing strategies in order to pick the pool
        JobPool routed = ticketRouter.route(category, priority);

        Ticket ticket = Ticket.builder()
                .student(student)
                .category(category)
                .routedPool(routed)
                .subject(form.getSubject().trim())
                .description(form.getDescription().trim())
                .status(TicketStatus.OPEN)
                .priority(priority)
                .ticketRef("PENDING") // placeholder until we know the generated id
                .build();
        Ticket saved = ticketRepository.save(ticket);
        saved.setTicketRef(String.format("TCK-%06d", saved.getId()));

        ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.CREATED, saved, student));
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
    @Transactional(readOnly = true)
    public Ticket getForStaff(Long id, User staff) {
        Ticket ticket = getById(id);
        if (!isAssignedTo(ticket, staff) && !(ticket.getAssignedStaff() == null && isInPool(ticket, staff))) {
            throw new AccessDeniedException("This ticket is not assigned to you or your work-pool.");
        }
        return ticket;
    }

    @Override
    @Transactional
    public Ticket assignToSelf(Long ticketId, User staff) {
        Ticket ticket = getById(ticketId);
        if (isAssignedTo(ticket, staff)) {
            return ticket; // already mine - nothing to do
        }
        if (ticket.getAssignedStaff() != null) {
            throw new BusinessRuleException("This ticket has already been taken by another staff member.");
        }
        if (!isInPool(ticket, staff)) {
            throw new AccessDeniedException("You are not in the work-pool that handles this ticket's category.");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleException("A closed ticket cannot be assigned.");
        }
        ticket.setAssignedStaff(staff);
        if (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.REOPENED) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.ASSIGNED, ticket, staff));
        return ticket;
    }

    @Override
    @Transactional
    public Ticket updateStatus(Long ticketId, String newStatus, User staff) {
        Ticket ticket = requireAssignedTo(getById(ticketId), staff);
        TicketStatus status;
        try {
            status = TicketStatus.valueOf(newStatus == null ? "" : newStatus.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown ticket status: " + newStatus);
        }
        if (!STAFF_SETTABLE.contains(status)) {
            throw new BusinessRuleException("Staff cannot set a ticket to " + status + ".");
        }
        if (!ALLOWED.get(ticket.getStatus()).contains(status)) {
            throw new BusinessRuleException("A ticket that is " + ticket.getStatus() + " cannot be changed to " + status + ".");
        }
        ticket.setStatus(status);
        if (status == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(java.time.LocalDateTime.now());
        }
        ticketEvents.notifyObservers(TicketEvent.statusChanged(ticket, staff, status));
        return ticket;
    }

    @Override
    @Transactional
    public TicketComment addComment(Long ticketId, User author, String comment, boolean resolutionNote, boolean internal) {
        Ticket ticket = getById(ticketId);
        boolean byStudent = author.getRole() == Role.STUDENT;
        if (!byStudent) {
            requireAssignedTo(ticket, author);
        } else if (!ticket.getStudent().getId().equals(author.getId())) {
            throw new AccessDeniedException("This ticket does not belong to you.");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleException("This ticket is closed and cannot receive new replies.");
        }
        String text = comment == null ? "" : comment.trim();
        if (text.isEmpty()) {
            throw new BusinessRuleException("Please write a reply before sending.");
        }
        if (text.length() > 2000) {
            throw new BusinessRuleException("A reply must be 2000 characters or fewer.");
        }
        boolean isInternal = internal && !byStudent;
        TicketComment ticketComment = TicketComment.builder()
                .ticket(ticket)
                .author(author)
                .comment(text)
                .resolutionNote(resolutionNote && !byStudent && !isInternal)
                .internal(isInternal)
                .build();
        TicketComment saved = commentRepository.save(ticketComment);

        if (byStudent) {
            // a reply on a resolved ticket means the student is not satisfied: reopen it
            if (ticket.getStatus() == TicketStatus.RESOLVED) {
                ticket.setStatus(TicketStatus.REOPENED);
                ticket.setResolvedAt(null);
            }
            ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.STUDENT_REPLIED, ticket, author));
        } else if (isInternal) {
            ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.INTERNAL_NOTE, ticket, author));
        } else {
            ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.STAFF_REPLIED, ticket, author));
        }
        return saved;
    }

    @Override
    @Transactional
    public Ticket release(Long ticketId, User staff) {
        Ticket ticket = requireAssignedTo(getById(ticketId), staff);
        if (ticket.getStatus() == TicketStatus.CLOSED || ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new BusinessRuleException("A " + ticket.getStatus() + " ticket cannot be released.");
        }
        ticket.setAssignedStaff(null);
        if (ticket.getStatus() == TicketStatus.IN_PROGRESS) {
            ticket.setStatus(TicketStatus.OPEN);
        }
        ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.RELEASED, ticket, staff));
        return ticket;
    }

    @Override
    @Transactional
    public Ticket transfer(Long ticketId, User staff, Long targetStaffId) {
        Ticket ticket = requireAssignedTo(getById(ticketId), staff);
        if (ticket.getStatus() == TicketStatus.CLOSED || ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new BusinessRuleException("A " + ticket.getStatus() + " ticket cannot be transferred.");
        }
        if (targetStaffId == null) {
            throw new BusinessRuleException("Choose a staff member to transfer the ticket to.");
        }
        User target = userRepository.findById(targetStaffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found."));
        if (target.getId().equals(staff.getId())) {
            throw new BusinessRuleException("The ticket is already assigned to you.");
        }
        if (target.getRole() != Role.STAFF || target.getStatus() != AccountStatus.ACTIVE || !isInPool(ticket, target)) {
            throw new BusinessRuleException("You can only transfer to an active member of this ticket's work-pool.");
        }
        ticket.setAssignedStaff(target);
        ticketEvents.notifyObservers(TicketEvent.transferred(ticket, staff, target));
        return ticket;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffLoad> poolWorkload(Ticket ticket) {
        JobPool pool = poolOf(ticket);
        if (pool == null) {
            return List.of();
        }
        return pool.getStaffMembers().stream()
                .filter(m -> m.getRole() == Role.STAFF && m.getStatus() == AccountStatus.ACTIVE)
                .sorted(java.util.Comparator.comparing(User::getFullName))
                .map(m -> new StaffLoad(m, ticketRepository.countActiveForStaff(m.getId())))
                .toList();
    }

    @Override
    public List<TicketComment> publicCommentsFor(Long ticketId) {
        return commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticketId);
    }

    @Override
    @Transactional
    public Ticket escalate(Long ticketId, User staff) {
        Ticket ticket = requireAssignedTo(getById(ticketId), staff);
        if (!ESCALATABLE.contains(ticket.getStatus())) {
            throw new BusinessRuleException("A ticket that is " + ticket.getStatus() + " cannot be escalated.");
        }
        ticket.setStatus(TicketStatus.ESCALATED);
        ticket.setPriority(Priority.HIGH);
        ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.ESCALATED, ticket, staff));
        return ticket;
    }

    @Override
    @Transactional
    public Ticket reopen(Long ticketId, User student) {
        Ticket ticket = getById(ticketId);
        if (!ticket.getStudent().getId().equals(student.getId())) {
            throw new AccessDeniedException("This ticket does not belong to you.");
        }
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new BusinessRuleException("Only a resolved ticket can be reopened.");
        }
        ticket.setStatus(TicketStatus.REOPENED);
        ticket.setResolvedAt(null); // resolution time is measured again when it is re-resolved
        ticketEvents.notifyObservers(TicketEvent.of(TicketEventType.REOPENED, ticket, student));
        return ticket;
    }

    // ---------- delete / edit / create on behalf ----------

    @Override
    @Transactional
    public void deleteTicket(Long ticketId, User actor) {
        Ticket ticket = getById(ticketId);
        boolean owner = ticket.getStudent().getId().equals(actor.getId());
        if (actor.getRole() == Role.STUDENT && !owner) {
            throw new AccessDeniedException("You can only delete your own tickets.");
        }
        String ref = ticket.getTicketRef();
        String subject = ticket.getSubject();
        User student = ticket.getStudent();
        User assigned = ticket.getAssignedStaff();

        // children first, then the ticket itself
        feedbackRepository.findByTicketId(ticketId).ifPresent(feedbackRepository::delete);
        commentRepository.deleteAll(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId));
        attachmentService.deleteAllForTicket(ticketId);
        notificationRepository.deleteAll(notificationRepository.findByRelatedEntityTypeAndRelatedEntityId("Ticket", ticketId));
        ticketRepository.delete(ticket);

        auditLogService.log(actor, "DELETE_TICKET", "Ticket", ticketId,
                ref + " - " + clip(subject, 120) + " (student: " + student.getFullName() + ")");

        if (!owner) {
            notificationService.notify(student, "TICKET_DELETED",
                    "Your ticket " + ref + " (" + clip(subject, 60) + ") was deleted by " + actor.getFullName() + ".",
                    null, null);
        }
        if (assigned != null && !assigned.getId().equals(actor.getId())) {
            notificationService.notify(assigned, "TICKET_DELETED",
                    "Ticket " + ref + " that you were handling was deleted by " + actor.getFullName() + ".",
                    null, null);
        }
    }

    @Override
    @Transactional
    public Ticket updateOwnTicket(Long ticketId, User student, TicketEditForm form) {
        Ticket ticket = getById(ticketId);
        if (!ticket.getStudent().getId().equals(student.getId())) {
            throw new AccessDeniedException("This ticket does not belong to you.");
        }
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleException("A " + ticket.getStatus() + " ticket can no longer be edited. "
                    + "Reply to reopen it, or raise a new ticket.");
        }
        TicketCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + form.getCategoryId()));
        Priority priority = form.getPriority() == null ? Priority.MEDIUM : form.getPriority();
        applyContent(ticket, category, priority, form.getSubject(), form.getDescription());
        auditLogService.log(student, "EDIT_TICKET", "Ticket", ticketId, ticket.getTicketRef() + " edited by the student");
        return ticket;
    }

    @Override
    @Transactional
    public Ticket createForStudent(ManagedTicketForm form, User actor) {
        if (form.getStudentId() == null) {
            throw new BusinessRuleException("Choose the student this ticket is for.");
        }
        User student = userRepository.findById(form.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        if (student.getRole() != Role.STUDENT) {
            throw new BusinessRuleException("Tickets can only be created for student accounts.");
        }
        TicketForm base = new TicketForm();
        base.setCategoryId(form.getCategoryId());
        base.setSubject(form.getSubject());
        base.setDescription(form.getDescription());
        base.setPriority(form.getPriority());
        Ticket ticket = createTicket(student, base);

        if (actor.getRole() == Role.ADMIN && form.getAssignedStaffId() != null) {
            applyAssignee(ticket, form.getAssignedStaffId());
        }
        if (form.getStatus() != null && form.getStatus() != TicketStatus.OPEN) {
            applyStatus(ticket, form.getStatus());
        }
        auditLogService.log(actor, "CREATE_TICKET_FOR_STUDENT", "Ticket", ticket.getId(),
                ticket.getTicketRef() + " created for " + student.getFullName());
        return ticket;
    }

    @Override
    @Transactional
    public Ticket updateManaged(Long ticketId, ManagedTicketForm form, User actor) {
        Ticket ticket = getById(ticketId);
        TicketCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + form.getCategoryId()));
        Priority priority = form.getPriority() == null ? Priority.MEDIUM : form.getPriority();

        String before = ticket.getCategory().getName() + "/" + ticket.getPriority() + "/" + ticket.getStatus()
                + "/" + (ticket.getAssignedStaff() == null ? "unassigned" : ticket.getAssignedStaff().getFullName());

        applyContent(ticket, category, priority, form.getSubject(), form.getDescription());
        if (form.getStatus() != null && form.getStatus() != ticket.getStatus()) {
            applyStatus(ticket, form.getStatus());
        }
        if (actor.getRole() == Role.ADMIN) {
            Long currentId = ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getId();
            if (!java.util.Objects.equals(currentId, form.getAssignedStaffId())) {
                applyAssignee(ticket, form.getAssignedStaffId());
            }
        }
        String after = ticket.getCategory().getName() + "/" + ticket.getPriority() + "/" + ticket.getStatus()
                + "/" + (ticket.getAssignedStaff() == null ? "unassigned" : ticket.getAssignedStaff().getFullName());
        auditLogService.log(actor, "UPDATE_TICKET", "Ticket", ticketId,
                ticket.getTicketRef() + " | before: " + before + " | after: " + after);
        return ticket;
    }

    @Override
    public boolean canTake(Ticket ticket, User staff) {
        return ticket.getAssignedStaff() == null && isInPool(ticket, staff);
    }

    /** Content edits; if category or priority changed the ticket is routed again. */
    private void applyContent(Ticket ticket, TicketCategory category, Priority priority, String subject, String description) {
        boolean reroute = !ticket.getCategory().getId().equals(category.getId()) || ticket.getPriority() != priority;
        ticket.setCategory(category);
        ticket.setPriority(priority);
        ticket.setSubject(subject.trim());
        ticket.setDescription(description.trim());
        if (reroute) {
            ticket.setRoutedPool(ticketRouter.route(category, priority));
        }
    }

    private void applyStatus(Ticket ticket, TicketStatus status) {
        ticket.setStatus(status);
        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            if (ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(java.time.LocalDateTime.now());
            }
        } else {
            ticket.setResolvedAt(null);
        }
    }

    private void applyAssignee(Ticket ticket, Long staffId) {
        if (staffId == null) {
            ticket.setAssignedStaff(null);
            return;
        }
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found."));
        if (staff.getRole() != Role.STAFF || staff.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Tickets can only be assigned to an active staff member.");
        }
        ticket.setAssignedStaff(staff);
        if (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.REOPENED) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        notificationService.notify(staff, "TICKET_ASSIGNED",
                "Ticket " + ticket.getTicketRef() + " was assigned to you.", "Ticket", ticket.getId());
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public List<TicketComment> commentsFor(Long ticketId) {
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Override
    public List<TicketStatus> nextStatuses(Ticket ticket) {
        return ALLOWED.get(ticket.getStatus()).stream()
                .filter(STAFF_SETTABLE::contains)
                .sorted()
                .toList();
    }

    @Override
    public boolean canEscalate(Ticket ticket) {
        return ESCALATABLE.contains(ticket.getStatus());
    }

    // ---------- access helpers ----------

    private static boolean isAssignedTo(Ticket ticket, User staff) {
        return ticket.getAssignedStaff() != null && ticket.getAssignedStaff().getId().equals(staff.getId());
    }

    /** The pool that handles this ticket: the one chosen by routing, else the category's default. */
    private static JobPool poolOf(Ticket ticket) {
        return ticket.getRoutedPool() != null ? ticket.getRoutedPool() : ticket.getCategory().getPool();
    }

    private static boolean isInPool(Ticket ticket, User staff) {
        JobPool pool = poolOf(ticket);
        return pool != null && pool.getStaffMembers().stream().anyMatch(s -> s.getId().equals(staff.getId()));
    }

    private static Ticket requireAssignedTo(Ticket ticket, User staff) {
        if (!isAssignedTo(ticket, staff)) {
            throw new AccessDeniedException("Take this ticket first - only the assigned staff member can change it.");
        }
        return ticket;
    }
}
