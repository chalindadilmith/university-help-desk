package com.helpdesk.controller;

import com.helpdesk.service.LostFoundService;
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

    public AdminDashboardController(UserService userService, ReportingService reportingService,
                                     LostFoundService lostFoundService) {
        this.userService = userService;
        this.reportingService = reportingService;
        this.lostFoundService = lostFoundService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("staffAdminCount", userService.findAllStaffAndAdmins().size());
        model.addAttribute("summary", reportingService.generateSummary());
        model.addAttribute("pendingClaims", lostFoundService.pendingClaims().size());
        return "admin/dashboard";
    }
}
