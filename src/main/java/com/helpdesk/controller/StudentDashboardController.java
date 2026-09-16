package com.helpdesk.controller;

import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.RegistrationService;
import com.helpdesk.service.TicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/student")
public class StudentDashboardController {

    private final TicketService ticketService;
    private final RegistrationService registrationService;

    public StudentDashboardController(TicketService ticketService, RegistrationService registrationService) {
        this.ticketService = ticketService;
        this.registrationService = registrationService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        var tickets = ticketService.myTickets(principal.getUser().getId());
        long openCount = tickets.stream().filter(t -> t.getStatus().name().equals("OPEN") || t.getStatus().name().equals("IN_PROGRESS")).count();

        model.addAttribute("totalTickets", tickets.size());
        model.addAttribute("openTickets", openCount);
        model.addAttribute("recentTickets", tickets.stream().limit(5).toList());
        model.addAttribute("hasOpenSemester", !registrationService.openSemesters().isEmpty());
        return "student/dashboard";
    }
}
