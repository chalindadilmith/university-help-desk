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

/**
 * Login page rendering is handled here; the actual authentication check on
 * POST /login is intercepted by Spring Security's filter chain before it
 * ever reaches a controller (see SecurityConfig - formLogin.loginPage).
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
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
