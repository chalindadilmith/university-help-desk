package com.helpdesk.controller;

import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.RegistrationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** UC-08: student browses open semesters and registers for courses. No payment step (Phase 0 scope decision). */
@Controller
@RequestMapping("/student/registration")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        var openSemesters = registrationService.openSemesters();
        model.addAttribute("openSemesters", openSemesters);
        model.addAttribute("myRegistrations", registrationService.myRegistrations(principal.getUser().getId()));

        if (!openSemesters.isEmpty()) {
            Long semesterId = openSemesters.get(0).getId();
            model.addAttribute("selectedSemester", openSemesters.get(0));
            model.addAttribute("offerings", registrationService.listOfferings(semesterId));
            model.addAttribute("existingRegistration",
                    registrationService.myRegistration(principal.getUser().getId(), semesterId).orElse(null));
        }
        return "student/registration";
    }

    @PostMapping
    public String register(@RequestParam Long semesterId,
                            @RequestParam(required = false) List<Long> offeringIds,
                            @AuthenticationPrincipal CustomUserDetails principal,
                            RedirectAttributes redirectAttributes) {
        try {
            registrationService.register(principal.getUser(), semesterId, offeringIds);
            redirectAttributes.addFlashAttribute("success", "Registration confirmed.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/registration";
    }
}
