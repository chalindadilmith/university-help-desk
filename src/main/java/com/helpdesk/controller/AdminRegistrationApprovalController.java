package com.helpdesk.controller;

import com.helpdesk.entity.RegistrationStatus;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.RegistrationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin reviews submitted semester registrations and approves or rejects them. */
@Controller
@RequestMapping("/admin/registrations")
public class AdminRegistrationApprovalController {

    private final RegistrationService registrationService;

    public AdminRegistrationApprovalController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** Defaults to the PENDING queue; status=ALL shows everything. */
    @GetMapping
    public String list(@RequestParam(required = false) Long semesterId,
                       @RequestParam(required = false, defaultValue = "PENDING") String status,
                       Model model) {
        RegistrationStatus statusFilter = "ALL".equalsIgnoreCase(status) ? null : parseStatus(status);
        model.addAttribute("registrations", registrationService.search(semesterId, statusFilter));
        model.addAttribute("semesters", registrationService.listSemesters());
        model.addAttribute("statuses", RegistrationStatus.values());
        model.addAttribute("selectedSemesterId", semesterId);
        model.addAttribute("selectedStatus", statusFilter == null ? "ALL" : statusFilter.name());
        model.addAttribute("pendingCount", registrationService.countPending());
        return "admin/registrations";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("registration", registrationService.getRegistration(id));
        return "admin/registration-detail";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String remarks,
                          @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes ra) {
        try {
            var r = registrationService.approve(id, remarks, principal.getUser());
            ra.addFlashAttribute("success", "Registration " + r.getReferenceNo() + " approved.");
            return "redirect:/admin/registrations";
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/registrations/" + id;
        }
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String remarks,
                         @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes ra) {
        try {
            var r = registrationService.reject(id, remarks, principal.getUser());
            ra.addFlashAttribute("success", "Registration " + r.getReferenceNo() + " rejected.");
            return "redirect:/admin/registrations";
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/registrations/" + id;
        }
    }

    private RegistrationStatus parseStatus(String value) {
        try {
            return RegistrationStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RegistrationStatus.PENDING;
        }
    }
}
