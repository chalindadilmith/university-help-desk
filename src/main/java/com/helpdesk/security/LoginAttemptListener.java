package com.helpdesk.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/** Feeds login results into {@link LoginAttemptService}. Only wrong-password/unknown-email failures count. */
@Component
public class LoginAttemptListener {

    private final LoginAttemptService attempts;

    public LoginAttemptListener(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        Authentication auth = event.getAuthentication();
        attempts.recordFailure(auth.getName(), remoteAddress(auth));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.recordSuccess(event.getAuthentication().getName());
    }

    private static String remoteAddress(Authentication auth) {
        return auth.getDetails() instanceof WebAuthenticationDetails d ? d.getRemoteAddress() : "unknown";
    }
}
