package com.helpdesk.security;

import com.helpdesk.entity.AccountStatus;
import com.helpdesk.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts our own User entity to the interface Spring Security actually
 * understands. We deliberately do NOT put fields directly on User that
 * implement UserDetails - keeping this adapter separate means the entity
 * stays a plain JPA class and Spring Security concerns stay in this package.
 */
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    /** Exposes the underlying entity so controllers can get id/name/etc. after @AuthenticationPrincipal. */
    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Each user has exactly one role in this system (Phase 0 decision:
        // simple enum roles, no fine-grained permission table).
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != AccountStatus.DEACTIVATED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == AccountStatus.ACTIVE;
    }
}
