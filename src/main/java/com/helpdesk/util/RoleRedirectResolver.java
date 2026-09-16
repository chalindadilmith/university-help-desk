package com.helpdesk.util;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Single place that decides "which dashboard does this role land on after
 * login". Used by both RoleBasedAuthenticationSuccessHandler (right after
 * login) and HomeController (when a logged-in user visits "/"), so the
 * mapping only has to be changed in one place if it ever changes.
 */
public final class RoleRedirectResolver {

    private RoleRedirectResolver() {
        // utility class - no instances
    }

    public static String resolve(Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority authority : authorities) {
            switch (authority.getAuthority()) {
                case "ROLE_STUDENT":
                    return "/student/dashboard";
                case "ROLE_STAFF":
                    return "/staff/dashboard";
                case "ROLE_ADMIN":
                    return "/admin/dashboard";
                default:
                    // fall through and keep looking
            }
        }
        return "/login";
    }
}
