package com.helpdesk.controller;

import com.helpdesk.dto.StaffCreateForm;
import com.helpdesk.dto.StaffCreationResult;
import com.helpdesk.entity.User;
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

/** UC-02: Admin creates/deactivates Staff and Admin accounts. */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAllStaffAndAdmins());
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
            model.addAttribute("users", userService.findAllStaffAndAdmins());
            return "admin/users";
        }
        try {
            StaffCreationResult result = userService.createStaffAccount(form, principal.getUser());
            redirectAttributes.addFlashAttribute("success",
                    "Account created for " + result.user().getEmail() +
                            ". Temporary password (share this with them securely, it will not be shown again): " +
                            result.temporaryPassword());
        } catch (DuplicateResourceException e) {
            bindingResult.rejectValue(e.getField(), "error.duplicate", e.getMessage());
            model.addAttribute("users", userService.findAllStaffAndAdmins());
            return "admin/users";
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        userService.deactivateUser(id, principal.getUser());
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        userService.activateUser(id, principal.getUser());
        return "redirect:/admin/users";
    }
}
