package com.helpdesk.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Catches exceptions that escape controllers so a normal user only ever
 * sees a friendly page - never a raw Java stack trace. Specific validation
 * errors (bad form input) are still handled locally in each controller via
 * BindingResult; this class is the safety net for everything else.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    /**
     * A controller can throw this directly (e.g. a Student trying to open
     * someone else's ticket) even though Spring Security's URL-pattern
     * rules in SecurityConfig only cover whole path prefixes, not
     * per-record ownership. Handled explicitly here so it renders the same
     * 403 page as a URL-level denial, instead of falling through to the
     * generic 500 handler below.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusinessRule(BusinessRuleException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/400";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        model.addAttribute("message", "Something went wrong. Please try again, and let the team know if it keeps happening.");
        return "error/500";
    }
}
