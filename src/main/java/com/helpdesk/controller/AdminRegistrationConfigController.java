package com.helpdesk.controller;

import com.helpdesk.dto.SemesterForm;
import com.helpdesk.dto.SpecialisationForm;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin sets up semesters (and opens/closes their registration window) and the
 * degree specialisations students pick from. Modules and offerings are managed
 * on the Module Offerings page (AdminModuleController).
 */
@Controller
@RequestMapping("/admin/registration-config")
public class AdminRegistrationConfigController {

    private static final String REDIRECT = "redirect:/admin/registration-config";

    private final RegistrationService registrationService;

    public AdminRegistrationConfigController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("semesters", registrationService.listSemesters());
        model.addAttribute("specialisations", registrationService.listSpecialisations());
        if (!model.containsAttribute("semesterForm")) model.addAttribute("semesterForm", new SemesterForm());
        if (!model.containsAttribute("specialisationForm")) model.addAttribute("specialisationForm", new SpecialisationForm());
        return "admin/registration-config";
    }

    @PostMapping("/semesters")
    public String createSemester(@Valid @ModelAttribute("semesterForm") SemesterForm form, BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            registrationService.createSemester(form, principal.getUser());
            redirectAttributes.addFlashAttribute("success", "Semester added.");
        } catch (BusinessRuleException | DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/semesters/{id}/toggle")
    public String toggleSemester(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        var semester = registrationService.toggleRegistrationOpen(id, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "Registration for " + semester.getName() + " is now " +
                (semester.isRegistrationOpen() ? "open." : "closed."));
        return REDIRECT;
    }

    @PostMapping("/specialisations")
    public String createSpecialisation(@Valid @ModelAttribute("specialisationForm") SpecialisationForm form,
                                       BindingResult bindingResult,
                                       @AuthenticationPrincipal CustomUserDetails principal,
                                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            registrationService.createSpecialisation(form, principal.getUser());
            redirectAttributes.addFlashAttribute("success", "Specialisation added.");
        } catch (DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/specialisations/{id}/toggle")
    public String toggleSpecialisation(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                                       RedirectAttributes redirectAttributes) {
        var spec = registrationService.toggleSpecialisationActive(id, principal.getUser());
        redirectAttributes.addFlashAttribute("success", spec.getName() + (spec.isActive() ? " is now available." : " is now hidden from students."));
        return REDIRECT;
    }
}
