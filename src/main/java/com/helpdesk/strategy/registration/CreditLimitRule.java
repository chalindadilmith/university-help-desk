package com.helpdesk.strategy.registration;

import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.service.RegistrationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** ConcreteStrategy: total credits may not exceed the per-semester limit. */
@Component
@Order(50)
public class CreditLimitRule implements RegistrationRule {

    @Override
    public void validate(RegistrationRequest r) {
        int total = r.totalCredits();
        if (total > RegistrationService.MAX_CREDITS_PER_SEMESTER) {
            throw new BusinessRuleException("Selected modules total " + total + " credits, which exceeds the "
                    + RegistrationService.MAX_CREDITS_PER_SEMESTER + "-credit limit per semester. Remove a module and try again.");
        }
    }
}
