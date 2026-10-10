package com.helpdesk.controller;

import com.helpdesk.dto.SemesterRegistrationForm;
import com.helpdesk.entity.CourseOffering;
import com.helpdesk.entity.Registration;
import com.helpdesk.entity.Semester;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.ModuleOfferingService;
import com.helpdesk.service.RegistrationService;
import com.helpdesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * UC-08: student checks eligibility, fills in the semester registration form
 * (specialisation, year, semester of study, contact, modules) and submits it
 * for Admin approval. No payment step (Phase 0 scope decision).
 */
@Controller
@RequestMapping("/student/registration")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final ModuleOfferingService moduleOfferingService;
    private final UserService userService;

    public RegistrationController(RegistrationService registrationService, ModuleOfferingService moduleOfferingService,
                                  UserService userService) {
        this.registrationService = registrationService;
        this.moduleOfferingService = moduleOfferingService;
        this.userService = userService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long semesterId,
                        @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        var student = userService.getById(principal.getUser().getId());
        List<Semester> openSemesters = registrationService.openSemesters();
        model.addAttribute("student", student);
        model.addAttribute("openSemesters", openSemesters);
        model.addAttribute("myRegistrations", registrationService.myRegistrations(student.getId()));
        model.addAttribute("maxCredits", RegistrationService.MAX_CREDITS_PER_SEMESTER);

        if (openSemesters.isEmpty()) {
            return "student/registration";
        }

        Semester selected = openSemesters.stream()
                .filter(s -> s.getId().equals(semesterId)).findFirst()
                .orElse(openSemesters.get(0));
        List<CourseOffering> offerings = moduleOfferingService.listOfferings(selected.getId());
        Registration existing = registrationService.myRegistration(student.getId(), selected.getId()).orElse(null);

        model.addAttribute("selectedSemester", selected);
        model.addAttribute("offerings", offerings);
        model.addAttribute("seatsTaken", moduleOfferingService.seatsTaken(offerings));
        model.addAttribute("existingRegistration", existing);
        model.addAttribute("eligibility", registrationService.checkEligibility(student, selected));
        model.addAttribute("specialisations", registrationService.activeSpecialisations());

        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", prefill(selected, existing));
        }
        return "student/registration";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("registrationForm") SemesterRegistrationForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        String back = "redirect:/student/registration" + (form.getSemesterId() != null ? "?semesterId=" + form.getSemesterId() : "");
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.registrationForm", bindingResult);
            redirectAttributes.addFlashAttribute("registrationForm", form);
            redirectAttributes.addFlashAttribute("error", "Please correct the highlighted fields.");
            return back;
        }
        try {
            Registration saved = registrationService.submit(principal.getUser(), form);
            redirectAttributes.addFlashAttribute("success", "Registration " + saved.getReferenceNo() +
                    " submitted (" + saved.getTotalCredits() + " credits). It will be confirmed once an admin approves it.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("registrationForm", form);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return back;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        try {
            registrationService.cancel(principal.getUser(), id);
            redirectAttributes.addFlashAttribute("success", "Your pending registration was cancelled.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/registration";
    }

    /** Pre-fills the form from the student's existing registration so a resubmit only needs changes. */
    private SemesterRegistrationForm prefill(Semester semester, Registration existing) {
        SemesterRegistrationForm form = new SemesterRegistrationForm();
        form.setSemesterId(semester.getId());
        if (existing != null) {
            form.setSpecialisationId(existing.getSpecialisation() != null ? existing.getSpecialisation().getId() : null);
            form.setAcademicYear(existing.getAcademicYear());
            form.setSemesterOfStudy(existing.getSemesterOfStudy());
            form.setContactNumber(existing.getContactNumber());
            form.setOfferingIds(existing.getCourseOfferings().stream().map(CourseOffering::getId).toList());
        }
        return form;
    }
}
