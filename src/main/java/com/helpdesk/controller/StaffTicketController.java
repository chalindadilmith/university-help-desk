package com.helpdesk.controller;

import com.helpdesk.dto.ManagedTicketForm;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.service.AdminConfigService;
import com.helpdesk.service.TicketAttachmentService;
import com.helpdesk.service.UserService;
import com.helpdesk.util.TicketListView;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.TicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * UC-09: Help Desk Staff work their assigned/pool ticket queue through to resolution.
 * Staff can also see every ticket in the system, create a ticket for a student, edit and delete tickets.
 */
@Controller
@RequestMapping("/staff/tickets")
public class StaffTicketController {

    /** Ready-made replies a staff member can insert into the reply box and then edit. */
    private static final List<String> CANNED_REPLIES = List.of(
            "Thank you for contacting the Help Desk. We are looking into this and will update you shortly.",
            "Could you please send more details (screenshots, course code or the exact error message) so we can investigate?",
            "This has now been resolved. Please check and let us know if the problem continues.",
            "We have passed your request to the relevant department and will update this ticket when we hear back.",
            "Please try again after clearing your browser cache and signing in once more. Reply here if it still fails.");

    private final TicketService ticketService;
    private final TicketAttachmentService attachmentService;
    private final AdminConfigService adminConfigService;
    private final UserService userService;

    public StaffTicketController(TicketService ticketService, TicketAttachmentService attachmentService,
                                 AdminConfigService adminConfigService, UserService userService) {
        this.ticketService = ticketService;
        this.attachmentService = attachmentService;
        this.adminConfigService = adminConfigService;
        this.userService = userService;
    }

    // ---------- my queue ----------

    @GetMapping
    public String queue(@AuthenticationPrincipal CustomUserDetails principal,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String sort,
                        @RequestParam(required = false) String scope,
                        @RequestParam(defaultValue = "1") int page,
                        Model model) {
        var queue = ticketService.staffQueue(principal.getUser().getId());
        TicketListView view = TicketListView.of(queue, q, status, sort, page, true, scope, null, null, null);
        model.addAttribute("tickets", view.getItems());
        model.addAttribute("view", view);
        model.addAttribute("queueTotal", queue.size());
        model.addAttribute("q", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("sort", sort);
        model.addAttribute("scope", scope);
        model.addAttribute("statuses", TicketStatus.values());
        return "staff/tickets";
    }

    // ---------- every ticket in the system ----------

    @GetMapping("/all")
    public String all(@RequestParam(required = false) String q,
                      @RequestParam(required = false) String status,
                      @RequestParam(required = false) String priority,
                      @RequestParam(required = false) Long categoryId,
                      @RequestParam(required = false) String assignee,
                      @RequestParam(required = false) String sort,
                      @RequestParam(required = false) String scope,
                      @RequestParam(defaultValue = "1") int page,
                      Model model) {
        var everything = ticketService.allTickets();
        TicketListView view = TicketListView.of(everything, q, status, sort, page, true, scope, priority, assignee, categoryId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            counts.put(s.name(), everything.stream().filter(t -> t.getStatus() == s).count());
        }
        model.addAttribute("tickets", view.getItems());
        model.addAttribute("view", view);
        model.addAttribute("totalAll", everything.size());
        model.addAttribute("statusCounts", counts);
        model.addAttribute("q", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("priorityFilter", priority);
        model.addAttribute("categoryFilter", categoryId);
        model.addAttribute("assigneeFilter", assignee);
        model.addAttribute("sort", sort);
        model.addAttribute("scope", scope);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", adminConfigService.listCategories());
        model.addAttribute("staffList", userService.findAllStaffAndAdmins().stream().filter(u -> u.getRole() == Role.STAFF).toList());
        return "staff/tickets-all";
    }

    // ---------- create / edit / delete (any ticket) ----------

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("ticketForm")) {
            model.addAttribute("ticketForm", new ManagedTicketForm());
        }
        populateForm(model, null);
        return "manage/ticket-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("ticketForm") ManagedTicketForm form, BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails principal, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Ticket created = ticketService.createForStudent(form, principal.getUser());
                redirectAttributes.addFlashAttribute("success", "Ticket " + created.getTicketRef() + " was created.");
                return "redirect:/staff/tickets/" + created.getId();
            } catch (BusinessRuleException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        populateForm(model, null);
        return "manage/ticket-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Ticket ticket = ticketService.getById(id);
        if (!model.containsAttribute("ticketForm")) {
            model.addAttribute("ticketForm", toForm(ticket));
        }
        populateForm(model, ticket);
        return "manage/ticket-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute("ticketForm") ManagedTicketForm form,
                       BindingResult bindingResult, @AuthenticationPrincipal CustomUserDetails principal,
                       Model model, RedirectAttributes redirectAttributes) {
        Ticket ticket = ticketService.getById(id);
        if (!bindingResult.hasErrors()) {
            try {
                ticketService.updateManaged(id, form, principal.getUser());
                redirectAttributes.addFlashAttribute("success", "Ticket " + ticket.getTicketRef() + " was updated.");
                return "redirect:/staff/tickets/" + id;
            } catch (BusinessRuleException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        populateForm(model, ticket);
        return "manage/ticket-form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        ticketService.deleteTicket(id, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "The ticket has been deleted.");
        return "redirect:/staff/tickets/all";
    }

    // ---------- one ticket ----------

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        // Staff may look at any ticket; only the assigned member (or a pool member taking it) can work on it.
        Ticket ticket = ticketService.getById(id);
        boolean mine = ticket.getAssignedStaff() != null
                && ticket.getAssignedStaff().getId().equals(principal.getUser().getId());
        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", ticketService.commentsFor(id));
        model.addAttribute("mine", mine);
        model.addAttribute("canTake", ticketService.canTake(ticket, principal.getUser()));
        model.addAttribute("nextStatuses", mine ? ticketService.nextStatuses(ticket) : List.of());
        model.addAttribute("canEscalate", mine && ticketService.canEscalate(ticket));
        model.addAttribute("attachments", attachmentService.forTicket(id));
        model.addAttribute("cannedReplies", CANNED_REPLIES);
        boolean active = ticket.getStatus() != TicketStatus.CLOSED && ticket.getStatus() != TicketStatus.RESOLVED;
        model.addAttribute("canRelease", mine && active);
        model.addAttribute("workload", mine && active
                ? ticketService.poolWorkload(ticket).stream()
                        .filter(w -> !w.staff().getId().equals(principal.getUser().getId())).toList()
                : List.of());
        return "staff/ticket-detail";
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        try {
            ticketService.assignToSelf(id, principal.getUser());
            redirectAttributes.addFlashAttribute("success", "You are now handling this ticket.");
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam String status,
                               @AuthenticationPrincipal CustomUserDetails principal,
                               RedirectAttributes redirectAttributes) {
        try {
            ticketService.updateStatus(id, status, principal.getUser());
            redirectAttributes.addFlashAttribute("success", "Status updated.");
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/comment")
    public String addComment(@PathVariable Long id, @RequestParam String comment,
                             @RequestParam(defaultValue = "false") boolean resolutionNote,
                             @RequestParam(defaultValue = "false") boolean internal,
                             @AuthenticationPrincipal CustomUserDetails principal,
                             RedirectAttributes redirectAttributes) {
        try {
            ticketService.addComment(id, principal.getUser(), comment, resolutionNote, internal);
            redirectAttributes.addFlashAttribute("success", internal ? "Internal note saved." : "Reply sent.");
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/escalate")
    public String escalate(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                           RedirectAttributes redirectAttributes) {
        try {
            ticketService.escalate(id, principal.getUser());
            redirectAttributes.addFlashAttribute("success", "Ticket escalated.");
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/release")
    public String release(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        try {
            ticketService.release(id, principal.getUser());
            redirectAttributes.addFlashAttribute("success", "The ticket is back in the queue.");
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/transfer")
    public String transfer(@PathVariable Long id, @RequestParam(required = false) Long targetStaffId,
                           @AuthenticationPrincipal CustomUserDetails principal,
                           RedirectAttributes redirectAttributes) {
        try {
            ticketService.transfer(id, principal.getUser(), targetStaffId);
            redirectAttributes.addFlashAttribute("success", "Ticket transferred.");
            return "redirect:/staff/tickets";
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/tickets/" + id;
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        ticketService.getById(id); // 404 if the ticket does not exist; staff may open any ticket's files
        return AttachmentResponses.file(attachmentService, attachmentService.get(id, attachmentId));
    }

    // ---------- helpers ----------

    private void populateForm(Model model, Ticket ticket) {
        model.addAttribute("ticket", ticket);
        model.addAttribute("basePath", "/staff/tickets");
        model.addAttribute("canAssign", false);
        model.addAttribute("categories", adminConfigService.listCategories());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("students", ticket == null
                ? userService.searchUsers(null, Role.STUDENT) : List.of());
        model.addAttribute("staffList", List.of());
    }

    static ManagedTicketForm toForm(Ticket ticket) {
        ManagedTicketForm form = new ManagedTicketForm();
        form.setCategoryId(ticket.getCategory().getId());
        form.setSubject(ticket.getSubject());
        form.setDescription(ticket.getDescription());
        form.setPriority(ticket.getPriority());
        form.setStatus(ticket.getStatus());
        form.setAssignedStaffId(ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getId());
        return form;
    }
}
