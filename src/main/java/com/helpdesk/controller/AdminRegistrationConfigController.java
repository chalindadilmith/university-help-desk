package com.helpdesk.controller;

import com.helpdesk.dto.CourseForm;
import com.helpdesk.dto.OfferingForm;
import com.helpdesk.dto.SemesterForm;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin sets up the semester/course/offering catalog that F6's student registration flow reads from. */
@Controller
@RequestMapping("/admin/registration-config")
public class AdminRegistrationConfigController {

    private final RegistrationService registrationService;

    public AdminRegistrationConfigController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    public String index(Model model) {
        var semesters = registrationService.listSemesters();
        model.addAttribute("semesters", semesters);
        model.addAttribute("courses", registrationService.listCourses());
        model.addAttribute("offerings", semesters.isEmpty() ? java.util.List.of() : registrationService.listOfferings(semesters.get(0).getId()));
        if (!model.containsAttribute("semesterForm")) model.addAttribute("semesterForm", new SemesterForm());
        if (!model.containsAttribute("courseForm")) model.addAttribute("courseForm", new CourseForm());
        if (!model.containsAttribute("offeringForm")) model.addAttribute("offeringForm", new OfferingForm());
        return "admin/registration-config";
    }

    @PostMapping("/semesters")
    public String createSemester(@Valid @ModelAttribute("semesterForm") SemesterForm form, BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill in all semester fields.");
            return "redirect:/admin/registration-config";
        }
        registrationService.createSemester(form);
        return "redirect:/admin/registration-config";
    }

    @PostMapping("/courses")
    public String createCourse(@Valid @ModelAttribute("courseForm") CourseForm form, BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill in all course fields.");
            return "redirect:/admin/registration-config";
        }
        try {
            registrationService.createCourse(form);
        } catch (DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/registration-config";
    }

    @PostMapping("/offerings")
    public String createOffering(@Valid @ModelAttribute("offeringForm") OfferingForm form, BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill in all offering fields.");
            return "redirect:/admin/registration-config";
        }
        registrationService.createOffering(form);
        return "redirect:/admin/registration-config";
    }
}
