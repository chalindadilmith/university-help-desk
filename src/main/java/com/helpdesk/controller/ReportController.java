package com.helpdesk.controller;

import com.helpdesk.service.ReportingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Shared by Staff and Admin (both need visibility into ticket/feedback
 * stats per UC-F5-01) - reachable at a neutral "/reports" path rather than
 * under /staff or /admin so both roles' SecurityConfig rules cover it via
 * the fallback anyRequest().authenticated() rule.
 */
@Controller
public class ReportController {

    private final ReportingService reportingService;

    public ReportController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("summary", reportingService.generateSummary());
        return "reports";
    }
}
