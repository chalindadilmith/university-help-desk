package com.helpdesk.config;

import com.helpdesk.repository.UserRepository;
import com.helpdesk.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * While an account still uses a temporary password (staff created by an admin, or
 * any account an admin reset), every page except the profile / change-password
 * screen and logout redirects to /profile. The flag is read from the database on
 * each request so an admin reset takes effect even for an already-open session.
 */
@Component
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public ForcePasswordChangeInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails principal)) {
            return true;
        }
        boolean mustChange = userRepository.findById(principal.getUser().getId())
                .map(u -> u.isMustChangePassword())
                .orElse(false);
        if (!mustChange) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/profile") || path.equals("/profile/password") || path.equals("/logout")) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/profile");
        return false;
    }
}
