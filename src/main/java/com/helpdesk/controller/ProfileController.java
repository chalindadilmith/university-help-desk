package com.helpdesk.controller;

import com.helpdesk.entity.User;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.UserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Available to any authenticated role - not restricted to one path prefix, so SecurityConfig's anyRequest().authenticated() covers it. */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String view(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("user", userService.getById(principal.getUser().getId()));
        return "profile";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String oldPassword,
                                  @RequestParam String newPassword,
                                  @RequestParam String confirmPassword,
                                  @AuthenticationPrincipal CustomUserDetails principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            User updated = userService.changePassword(principal.getUser().getId(), oldPassword, newPassword, confirmPassword);
            // keep the session's cached copy in step so nothing still treats the password as temporary
            principal.getUser().setPasswordHash(updated.getPasswordHash());
            principal.getUser().setMustChangePassword(false);
            redirectAttributes.addFlashAttribute("success", "Password changed.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("passwordError", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping
    public String update(@RequestParam @NotBlank String fullName,
                          @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        userService.updateOwnProfile(principal.getUser().getId(), fullName);
        redirectAttributes.addFlashAttribute("success", "Profile updated.");
        return "redirect:/profile";
    }
}
