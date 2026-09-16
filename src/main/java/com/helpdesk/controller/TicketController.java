package com.helpdesk.controller;

import com.helpdesk.dto.TicketForm;
import com.helpdesk.entity.Ticket;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.AdminConfigService;
import com.helpdesk.service.FeedbackService;
import com.helpdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC-F2-01: student creates and tracks support tickets. */
@Controller
@RequestMapping("/student/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final AdminConfigService adminConfigService;
    private final FeedbackService feedbackService;

    public TicketController(TicketService ticketService, AdminConfigService adminConfigService,
                             FeedbackService feedbackService) {
        this.ticketService = ticketService;
        this.adminConfigService = adminConfigService;
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("tickets", ticketService.myTickets(principal.getUser().getId()));
        model.addAttribute("categories", adminConfigService.listCategories());
        if (!model.containsAttribute("ticketForm")) {
            model.addAttribute("ticketForm", new TicketForm());
        }
        return "student/tickets";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("ticketForm") TicketForm form, BindingResult bindingResult,
                          @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("tickets", ticketService.myTickets(principal.getUser().getId()));
            model.addAttribute("categories", adminConfigService.listCategories());
            return "student/tickets";
        }
        Ticket ticket = ticketService.createTicket(principal.getUser(), form);
        return "redirect:/student/tickets/" + ticket.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        Ticket ticket = ticketService.getById(id);
        if (!ticket.getStudent().getId().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("This ticket does not belong to you.");
        }
        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", ticketService.commentsFor(id));
        model.addAttribute("existingFeedback", feedbackService.findByTicketId(id).orElse(null));
        return "student/ticket-detail";
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        Ticket ticket = ticketService.getById(id);
        if (!ticket.getStudent().getId().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("This ticket does not belong to you.");
        }
        ticketService.reopen(id, principal.getUser());
        return "redirect:/student/tickets/" + id;
    }

    @PostMapping("/{id}/feedback")
    public String submitFeedback(@PathVariable Long id, @RequestParam int rating, @RequestParam(required = false) String comment,
                                  @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        Ticket ticket = ticketService.getById(id);
        if (!ticket.getStudent().getId().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("This ticket does not belong to you.");
        }
        try {
            feedbackService.submitFeedback(ticket, principal.getUser(), rating, comment);
            redirectAttributes.addFlashAttribute("success", "Thanks for your feedback!");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/tickets/" + id;
    }
}
