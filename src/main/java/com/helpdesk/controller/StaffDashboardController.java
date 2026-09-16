package com.helpdesk.controller;

import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.TicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/staff")
public class StaffDashboardController {

    private final TicketService ticketService;

    public StaffDashboardController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        var queue = ticketService.staffQueue(principal.getUser().getId());
        long unassigned = queue.stream().filter(t -> t.getAssignedStaff() == null).count();
        long mine = queue.stream().filter(t -> t.getAssignedStaff() != null
                && t.getAssignedStaff().getId().equals(principal.getUser().getId())).count();

        model.addAttribute("queueSize", queue.size());
        model.addAttribute("unassignedCount", unassigned);
        model.addAttribute("myTicketCount", mine);
        model.addAttribute("recentQueue", queue.stream().limit(5).toList());
        return "staff/dashboard";
    }
}
