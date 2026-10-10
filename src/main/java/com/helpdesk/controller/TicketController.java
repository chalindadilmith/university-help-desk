package com.helpdesk.controller;

import com.helpdesk.dto.TicketEditForm;
import com.helpdesk.dto.TicketForm;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.AdminConfigService;
import com.helpdesk.service.FeedbackService;
import com.helpdesk.service.TicketAttachmentService;
import com.helpdesk.service.TicketService;
import com.helpdesk.util.TicketListView;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


/** UC-F2-01: student creates and tracks support tickets. */
@Controller
@RequestMapping("/student/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final AdminConfigService adminConfigService;
    private final FeedbackService feedbackService;
    private final TicketAttachmentService attachmentService;

    public TicketController(TicketService ticketService, AdminConfigService adminConfigService,
                             FeedbackService feedbackService, TicketAttachmentService attachmentService) {
        this.ticketService = ticketService;
        this.adminConfigService = adminConfigService;
        this.feedbackService = feedbackService;
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails principal,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String scope,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        populateList(principal, q, status, sort, scope, page, model);
        if (!model.containsAttribute("ticketForm")) {
            model.addAttribute("ticketForm", new TicketForm());
        }
        return "student/tickets";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("ticketForm") TicketForm form, BindingResult bindingResult,
                          @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                attachmentService.validate(form.getAttachments());
            } catch (BusinessRuleException e) {
                bindingResult.addError(new FieldError("ticketForm", "attachments", e.getMessage()));
            }
        }
        if (bindingResult.hasErrors()) {
            populateList(principal, null, null, null, null, 1, model);
            return "student/tickets";
        }
        Ticket ticket = ticketService.createTicket(principal.getUser(), form);
        try {
            attachmentService.storeAll(ticket, form.getAttachments());
        } catch (BusinessRuleException e) {
            // the ticket itself is already saved; tell the student the files did not make it
            return "redirect:/student/tickets/" + ticket.getId() + "?attachmentFailed";
        }
        return "redirect:/student/tickets/" + ticket.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @RequestParam(required = false) String attachmentFailed,
                         @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        Ticket ticket = ownedTicket(id, principal);
        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", ticketService.publicCommentsFor(id));
        model.addAttribute("attachments", attachmentService.forTicket(id));
        model.addAttribute("canReply", ticket.getStatus() != TicketStatus.CLOSED);
        model.addAttribute("attachmentFailed", attachmentFailed != null);
        model.addAttribute("existingFeedback", feedbackService.findByTicketId(id).orElse(null));
        model.addAttribute("canEdit", ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED);
        return "student/ticket-detail";
    }

    /** The student corrects a ticket they raised (UC-F2-01: full CRUD on own tickets). */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        Ticket ticket = ownedTicket(id, principal);
        if (!model.containsAttribute("editForm")) {
            TicketEditForm form = new TicketEditForm();
            form.setCategoryId(ticket.getCategory().getId());
            form.setSubject(ticket.getSubject());
            form.setDescription(ticket.getDescription());
            form.setPriority(ticket.getPriority());
            model.addAttribute("editForm", form);
        }
        model.addAttribute("ticket", ticket);
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", adminConfigService.listCategories());
        return "student/ticket-edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute("editForm") TicketEditForm form,
                       BindingResult bindingResult, @AuthenticationPrincipal CustomUserDetails principal,
                       Model model, RedirectAttributes redirectAttributes) {
        Ticket ticket = ownedTicket(id, principal);
        if (!bindingResult.hasErrors()) {
            try {
                ticketService.updateOwnTicket(id, principal.getUser(), form);
                redirectAttributes.addFlashAttribute("success", "Your ticket has been updated.");
                return "redirect:/student/tickets/" + id;
            } catch (BusinessRuleException e) {
                redirectAttributes.addFlashAttribute("error", e.getMessage());
                return "redirect:/student/tickets/" + id;
            }
        }
        model.addAttribute("ticket", ticket);
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", adminConfigService.listCategories());
        return "student/ticket-edit";
    }

    /** A student can delete their own ticket, e.g. one submitted by accident. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        ownedTicket(id, principal);
        ticketService.deleteTicket(id, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "The ticket has been deleted.");
        return "redirect:/student/tickets";
    }

    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam String comment,
                        @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        ownedTicket(id, principal);
        try {
            ticketService.addComment(id, principal.getUser(), comment, false, false);
            redirectAttributes.addFlashAttribute("success", "Your reply has been sent.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/tickets/" + id;
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        ownedTicket(id, principal);
        return AttachmentResponses.file(attachmentService, attachmentService.get(id, attachmentId));
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        ownedTicket(id, principal);
        try {
            ticketService.reopen(id, principal.getUser()); // only allowed while the ticket is Resolved
            redirectAttributes.addFlashAttribute("success", "Your ticket has been reopened.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/tickets/" + id;
    }

    @PostMapping("/{id}/feedback")
    public String submitFeedback(@PathVariable Long id, @RequestParam int rating, @RequestParam(required = false) String comment,
                                  @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        Ticket ticket = ownedTicket(id, principal);
        try {
            feedbackService.submitFeedback(ticket, principal.getUser(), rating, comment);
            redirectAttributes.addFlashAttribute("success", "Thanks for your feedback!");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/tickets/" + id;
    }

    // ---------- helpers ----------

    private Ticket ownedTicket(Long id, CustomUserDetails principal) {
        Ticket ticket = ticketService.getById(id);
        if (!ticket.getStudent().getId().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("This ticket does not belong to you.");
        }
        return ticket;
    }

    private void populateList(CustomUserDetails principal, String q, String status, String sort, String scope,
                              int page, Model model) {
        var mine = ticketService.myTickets(principal.getUser().getId());
        TicketListView view = TicketListView.of(mine, q, status, sort, page, false, scope, null, null, null);
        model.addAttribute("totalMine", mine.size());
        model.addAttribute("scope", scope);
        model.addAttribute("tickets", view.getItems());
        model.addAttribute("view", view);
        model.addAttribute("q", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("sort", sort);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", adminConfigService.listCategories());
    }
}
