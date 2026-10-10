package com.helpdesk.controller;

import com.helpdesk.dto.CourseForm;
import com.helpdesk.dto.OfferingForm;
import com.helpdesk.entity.Semester;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.ModuleOfferingService;
import com.helpdesk.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Module Offerings page: Admin adds, updates and deletes modules and the modules offered per semester. */
@Controller
@RequestMapping("/admin/modules")
public class AdminModuleController {

    private final ModuleOfferingService moduleService;
    private final RegistrationService registrationService;

    public AdminModuleController(ModuleOfferingService moduleService, RegistrationService registrationService) {
        this.moduleService = moduleService;
        this.registrationService = registrationService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long semesterId, Model model) {
        List<Semester> semesters = registrationService.listSemesters();
        Semester selected = semesters.stream().filter(s -> s.getId().equals(semesterId)).findFirst()
                .orElse(semesters.isEmpty() ? null : semesters.get(0));
        var offerings = moduleService.listOfferings(selected == null ? null : selected.getId());

        model.addAttribute("semesters", semesters);
        model.addAttribute("selectedSemester", selected);
        model.addAttribute("courses", moduleService.listCourses());
        model.addAttribute("offerings", offerings);
        model.addAttribute("seatsTaken", moduleService.seatsTaken(offerings));
        model.addAttribute("specialisations", registrationService.listSpecialisations());
        if (!model.containsAttribute("courseForm")) model.addAttribute("courseForm", new CourseForm());
        if (!model.containsAttribute("offeringForm")) {
            OfferingForm form = new OfferingForm();
            if (selected != null) form.setSemesterId(selected.getId());
            model.addAttribute("offeringForm", form);
        }
        return "admin/modules";
    }

    // ------------------------------------------------------------- modules

    @PostMapping("/courses")
    public String createCourse(@Valid @ModelAttribute("courseForm") CourseForm form, BindingResult bindingResult,
                               @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            ra.addFlashAttribute("courseForm", form);
            return "redirect:/admin/modules";
        }
        try {
            var course = moduleService.createCourse(form, principal.getUser());
            ra.addFlashAttribute("success", "Module " + course.getCode() + " added.");
        } catch (DuplicateResourceException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("courseForm", form);
        }
        return "redirect:/admin/modules";
    }

    @GetMapping("/courses/{id}/edit")
    public String editCourse(@PathVariable Long id, Model model) {
        model.addAttribute("course", moduleService.getCourse(id));
        if (!model.containsAttribute("courseForm")) model.addAttribute("courseForm", moduleService.toForm(moduleService.getCourse(id)));
        model.addAttribute("specialisations", registrationService.listSpecialisations());
        return "admin/course-edit";
    }

    @PostMapping("/courses/{id}")
    public String updateCourse(@PathVariable Long id, @Valid @ModelAttribute("courseForm") CourseForm form,
                               BindingResult bindingResult, @AuthenticationPrincipal CustomUserDetails principal,
                               Model model, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("course", moduleService.getCourse(id));
            model.addAttribute("specialisations", registrationService.listSpecialisations());
            return "admin/course-edit";
        }
        try {
            var course = moduleService.updateCourse(id, form, principal.getUser());
            ra.addFlashAttribute("success", "Module " + course.getCode() + " updated.");
            return "redirect:/admin/modules";
        } catch (DuplicateResourceException e) {
            bindingResult.rejectValue(e.getField(), "duplicate", e.getMessage());
            model.addAttribute("course", moduleService.getCourse(id));
            model.addAttribute("specialisations", registrationService.listSpecialisations());
            return "admin/course-edit";
        }
    }

    @PostMapping("/courses/{id}/delete")
    public String deleteCourse(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                               RedirectAttributes ra) {
        try {
            moduleService.deleteCourse(id, principal.getUser());
            ra.addFlashAttribute("success", "Module deleted.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/modules";
    }

    // ------------------------------------------------------------- offerings

    @PostMapping("/offerings")
    public String createOffering(@Valid @ModelAttribute("offeringForm") OfferingForm form, BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes ra) {
        String back = "redirect:/admin/modules" + (form.getSemesterId() != null ? "?semesterId=" + form.getSemesterId() : "");
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return back;
        }
        try {
            var offering = moduleService.createOffering(form, principal.getUser());
            ra.addFlashAttribute("success", offering.getCourse().getCode() + " is now offered in " + offering.getSemester().getName() + ".");
        } catch (DuplicateResourceException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return back;
    }

    @GetMapping("/offerings/{id}/edit")
    public String editOffering(@PathVariable Long id, Model model) {
        var offering = moduleService.getOffering(id);
        model.addAttribute("offering", offering);
        if (!model.containsAttribute("offeringForm")) model.addAttribute("offeringForm", moduleService.toForm(offering));
        model.addAttribute("semesters", registrationService.listSemesters());
        model.addAttribute("courses", moduleService.listCourses());
        return "admin/offering-edit";
    }

    @PostMapping("/offerings/{id}")
    public String updateOffering(@PathVariable Long id, @Valid @ModelAttribute("offeringForm") OfferingForm form,
                                 BindingResult bindingResult, @AuthenticationPrincipal CustomUserDetails principal,
                                 Model model, RedirectAttributes ra) {
        if (!bindingResult.hasErrors()) {
            try {
                var offering = moduleService.updateOffering(id, form, principal.getUser());
                ra.addFlashAttribute("success", "Offering " + offering.getCourse().getCode() + " updated.");
                return "redirect:/admin/modules?semesterId=" + offering.getSemester().getId();
            } catch (DuplicateResourceException | BusinessRuleException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("offering", moduleService.getOffering(id));
        model.addAttribute("semesters", registrationService.listSemesters());
        model.addAttribute("courses", moduleService.listCourses());
        return "admin/offering-edit";
    }

    @PostMapping("/offerings/{id}/delete")
    public String deleteOffering(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes ra) {
        Long semesterId = moduleService.getOffering(id).getSemester().getId();
        try {
            moduleService.deleteOffering(id, principal.getUser());
            ra.addFlashAttribute("success", "Offering deleted.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/modules?semesterId=" + semesterId;
    }
}
