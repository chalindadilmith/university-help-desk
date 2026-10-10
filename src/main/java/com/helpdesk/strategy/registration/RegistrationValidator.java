package com.helpdesk.strategy.registration;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strategy pattern (F6) - Context role.
 * Runs every rule in @Order sequence; the first rule that fails stops the check by throwing.
 * Adding a rule (e.g. prerequisites, one of the open issues in Phase 0) = one new @Component class.
 */
@Component
public class RegistrationValidator {

    private final List<RegistrationRule> rules;

    public RegistrationValidator(List<RegistrationRule> rules) {
        this.rules = rules;
    }

    public void validate(RegistrationRequest request) {
        for (RegistrationRule rule : rules) {
            rule.validate(request);
        }
    }
}
