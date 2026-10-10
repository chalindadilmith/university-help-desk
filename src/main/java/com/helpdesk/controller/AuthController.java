package com.helpdesk.controller;

import com.helpdesk.dto.RegisterForm;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login page rendering is handled here; the actual authentication check on
 * POST /login is intercepted by Spring Security's filter chain before it
 * ever reaches a controller (see SecurityConfig - formLogin.loginPage).
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final com.helpdesk.service.PasswordResetService passwordResetService;

    public AuthController(UserService userService, com.helpdesk.service.PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgot(@RequestParam(required = false) String email,
                         org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        passwordResetService.requestReset(email);
        // same answer whether or not the email exists
        redirectAttributes.addFlashAttribute("sent", true);
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPage(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", passwordResetService.isValid(token));
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam String token, @RequestParam String password,
                        @RequestParam String confirmPassword, Model model) {
        try {
            passwordResetService.resetPassword(token, password, confirmPassword);
            return "redirect:/login?reset";
        } catch (com.helpdesk.exception.BusinessRuleException e) {
            model.addAttribute("token", token);
            model.addAttribute("valid", passwordResetService.isValid(token));
            model.addAttribute("error", e.getMessage());
            return "auth/reset-password";
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                            BindingResult bindingResult) {

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.registerStudent(form);
        } catch (DuplicateResourceException e) {
            bindingResult.rejectValue(e.getField(), "error.duplicate", e.getMessage());
            return "auth/register";
        }

        return "redirect:/login?registered";
    }
}
