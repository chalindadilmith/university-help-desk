package com.helpdesk.controller;

import com.helpdesk.entity.Ticket;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.TicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** UC-09: Help Desk Staff work their assigned/pool ticket queue through to resolution. */
@Controller
@RequestMapping("/staff/tickets")
public class StaffTicketController {

    private final TicketService ticketService;

    public StaffTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public String queue(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("tickets", ticketService.staffQueue(principal.getUser().getId()));
        return "staff/tickets";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("ticket", ticketService.getById(id));
        model.addAttribute("comments", ticketService.commentsFor(id));
        return "staff/ticket-detail";
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        ticketService.assignToSelf(id, principal.getUser());
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam String status,
                                @AuthenticationPrincipal CustomUserDetails principal) {
        ticketService.updateStatus(id, status, principal.getUser());
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/comment")
    public String addComment(@PathVariable Long id, @RequestParam String comment,
                              @RequestParam(defaultValue = "false") boolean resolutionNote,
                              @AuthenticationPrincipal CustomUserDetails principal) {
        ticketService.addComment(id, principal.getUser(), comment, resolutionNote);
        return "redirect:/staff/tickets/" + id;
    }

    @PostMapping("/{id}/escalate")
    public String escalate(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        ticketService.escalate(id, principal.getUser());
        return "redirect:/staff/tickets/" + id;
    }
}
