package com.helpdesk.strategy.registration;

import com.helpdesk.entity.CourseOffering;
import com.helpdesk.exception.BusinessRuleException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** ConcreteStrategy: every chosen module must belong to the semester being registered. */
@Component
@Order(10)
public class OfferedInSemesterRule implements RegistrationRule {

    @Override
    public void validate(RegistrationRequest r) {
        for (CourseOffering o : r.offerings()) {
            if (!o.getSemester().getId().equals(r.semester().getId())) {
                throw new BusinessRuleException(o.getCourse().getCode() + " is not offered in " + r.semester().getName() + ".");
            }
        }
    }
}
