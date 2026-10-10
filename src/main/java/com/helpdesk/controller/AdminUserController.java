package com.helpdesk.controller;

import com.helpdesk.dto.StaffCreateForm;
import com.helpdesk.dto.StaffCreationResult;
import com.helpdesk.dto.UserEditForm;
import com.helpdesk.entity.User;
import com.helpdesk.entity.Role;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC-02: Admin creates Staff/Admin accounts and manages (search, deactivate, reset password) all users, students included. */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final com.helpdesk.repository.JobPoolRepository jobPoolRepository;

    public AdminUserController(UserService userService, com.helpdesk.repository.JobPoolRepository jobPoolRepository) {
        this.userService = userService;
        this.jobPoolRepository = jobPoolRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                        @AuthenticationPrincipal CustomUserDetails principal,
                        Model model) {
        populateList(model, keyword, null, principal);
        if (!model.containsAttribute("staffForm")) {
            model.addAttribute("staffForm", new StaffCreateForm());
        }
        return "admin/users";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("staffForm") StaffCreateForm form,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal CustomUserDetails principal,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateList(model, null, null, principal);
            return "admin/users";
        }
        try {
            form.setRole(Role.STAFF); // roles are no longer picked here; work-pools are managed on the Work Pools page
            StaffCreationResult result = userService.createStaffAccount(form, principal.getUser());
            redirectAttributes.addFlashAttribute("success",
                    "Account created for " + result.user().getEmail() +
                            ". Temporary password (share this with them securely, it will not be shown again): " +
                            result.temporaryPassword() + ". They must change it at first login.");
        } catch (DuplicateResourceException e) {
            bindingResult.rejectValue(e.getField(), "error.duplicate", e.getMessage());
            populateList(model, null, null, principal);
            return "admin/users";
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.getById(id);
        if (!model.containsAttribute("userForm")) {
            UserEditForm form = new UserEditForm();
            form.setFullName(user.getFullName());
            form.setEmail(user.getEmail());
            form.setUniversityId(user.getUniversityId());
            model.addAttribute("userForm", form);
        }
        model.addAttribute("editUser", user);
        return "admin/user-edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute("userForm") UserEditForm form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal CustomUserDetails principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                User saved = userService.updateUser(id, form, principal.getUser());
                redirectAttributes.addFlashAttribute("success", "Account updated for " + saved.getEmail() + ".");
                return "redirect:/admin/users";
            } catch (DuplicateResourceException e) {
                bindingResult.rejectValue(e.getField(), "error.duplicate", e.getMessage());
            } catch (BusinessRuleException e) {
                bindingResult.rejectValue("role", "error.role", e.getMessage());
            }
        }
        model.addAttribute("editUser", userService.getById(id));
        return "admin/user-edit";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                              RedirectAttributes redirectAttributes) {
        try {
            userService.deactivateUser(id, principal.getUser());
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        userService.activateUser(id, principal.getUser());
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            StaffCreationResult result = userService.resetPassword(id, principal.getUser());
            redirectAttributes.addFlashAttribute("success",
                    "Password reset for " + result.user().getEmail() +
                            ". Temporary password (share it securely, it will not be shown again): " +
                            result.temporaryPassword() + ". They must change it at next login.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    private void populateList(Model model, String keyword, Role role, CustomUserDetails principal) {
        model.addAttribute("users", userService.searchUsers(keyword, role));
        model.addAttribute("keyword", keyword);
        // a staff member's "role" is the work-pool(s) they belong to - shown here, managed on the Work Pools page
        java.util.Map<Long, java.util.List<String>> poolsByUser = new java.util.HashMap<>();
        for (com.helpdesk.entity.JobPool pool : jobPoolRepository.findAllByOrderByNameAsc()) {
            for (User member : pool.getStaffMembers()) {
                poolsByUser.computeIfAbsent(member.getId(), k -> new java.util.ArrayList<>()).add(pool.getName());
            }
        }
        model.addAttribute("poolsByUser", poolsByUser);
        model.addAttribute("currentUserId", principal.getUser().getId());
    }
}
