package com.helpdesk.controller;

import com.helpdesk.service.LostFoundService;
import com.helpdesk.service.RegistrationService;
import com.helpdesk.service.ReportingService;
import com.helpdesk.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final UserService userService;
    private final ReportingService reportingService;
    private final LostFoundService lostFoundService;
    private final RegistrationService registrationService;
    private final com.helpdesk.service.TicketService ticketService;

    public AdminDashboardController(UserService userService, ReportingService reportingService,
                                     LostFoundService lostFoundService, RegistrationService registrationService,
                                     com.helpdesk.service.TicketService ticketService) {
        this.ticketService = ticketService;
        this.userService = userService;
        this.reportingService = reportingService;
        this.lostFoundService = lostFoundService;
        this.registrationService = registrationService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("staffAdminCount", userService.findAllStaffAndAdmins().size());
        model.addAttribute("summary", reportingService.generateSummary());
        model.addAttribute("pendingClaims", lostFoundService.pendingClaims().size());
        model.addAttribute("pendingRegistrations", registrationService.countPending());
        var tickets = ticketService.allTickets();
        java.util.function.Predicate<com.helpdesk.entity.Ticket> active = t ->
                t.getStatus() != com.helpdesk.entity.TicketStatus.RESOLVED && t.getStatus() != com.helpdesk.entity.TicketStatus.CLOSED;
        model.addAttribute("activeTickets", tickets.stream().filter(active).count());
        model.addAttribute("unassignedTickets", tickets.stream().filter(active)
                .filter(t -> t.getAssignedStaff() == null).count());
        model.addAttribute("escalatedTickets", tickets.stream()
                .filter(t -> t.getStatus() == com.helpdesk.entity.TicketStatus.ESCALATED).count());
        java.time.LocalDateTime threeDaysAgo = java.time.LocalDateTime.now().minusDays(3);
        model.addAttribute("oldTickets", tickets.stream().filter(active)
                .filter(t -> t.getCreatedAt().isBefore(threeDaysAgo)).count());
        java.util.Map<String, Long> statusCounts = new java.util.LinkedHashMap<>();
        for (com.helpdesk.entity.TicketStatus s : com.helpdesk.entity.TicketStatus.values()) {
            statusCounts.put(s.name(), tickets.stream().filter(t -> t.getStatus() == s).count());
        }
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("totalTickets", tickets.size());
        model.addAttribute("attention", tickets.stream().filter(active)
                .filter(t -> t.getAssignedStaff() == null || t.getStatus() == com.helpdesk.entity.TicketStatus.ESCALATED)
                .limit(8).toList());
        return "admin/dashboard";
    }
}
