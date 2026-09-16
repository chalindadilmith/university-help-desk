package com.helpdesk.controller;

import com.helpdesk.util.RoleRedirectResolver;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * "/" is public (see SecurityConfig), but if someone is already logged in
 * we send them straight to their dashboard instead of showing a landing page.
 * An anonymous visitor's Authentication object still exists (Spring Security
 * always populates one) with a ROLE_ANONYMOUS authority, which
 * RoleRedirectResolver doesn't recognise, so it falls back to "/login".
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        return "redirect:" + RoleRedirectResolver.resolve(authentication.getAuthorities());
    }
}
