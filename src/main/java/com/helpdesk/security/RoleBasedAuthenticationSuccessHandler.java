package com.helpdesk.security;

import com.helpdesk.util.RoleRedirectResolver;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Runs immediately after Spring Security confirms a login is valid.
 * Sends the user to their own dashboard instead of one fixed page, since
 * Student/Staff/Admin each have a completely different home screen.
 */
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        String target = RoleRedirectResolver.resolve(authentication.getAuthorities());
        response.sendRedirect(request.getContextPath() + target);
    }
}
