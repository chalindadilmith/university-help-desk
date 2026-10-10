package com.helpdesk.strategy.registration;

/**
 * Strategy pattern (F6 Semester Registration) - Strategy role.
 * One validation rule (an interchangeable "algorithm" for checking a selection).
 * Throws BusinessRuleException with a student-friendly message when the rule fails.
 */
public interface RegistrationRule {

    void validate(RegistrationRequest request);
}
