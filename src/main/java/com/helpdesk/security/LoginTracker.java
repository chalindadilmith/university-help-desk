package com.helpdesk.security;

import com.helpdesk.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Remembers when each user last signed in, so member reports can show it. */
@Component
public class LoginTracker {

    private final UserRepository userRepository;

    public LoginTracker(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener
    @Transactional
    public void onLogin(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CustomUserDetails details) {
            userRepository.findById(details.getUser().getId()).ifPresent(u -> u.setLastLoginAt(LocalDateTime.now()));
        }
    }
}
