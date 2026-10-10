package com.helpdesk.strategy.registration;

import com.helpdesk.entity.Course;
import com.helpdesk.entity.CourseOffering;
import com.helpdesk.exception.BusinessRuleException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** ConcreteStrategy: module must be open to the student's specialisation and academic year. */
@Component
@Order(20)
public class CourseAvailabilityRule implements RegistrationRule {

    @Override
    public void validate(RegistrationRequest r) {
        for (CourseOffering o : r.offerings()) {
            Course course = o.getCourse();
            if (!course.isAvailableFor(r.specialisation().getId(), r.academicYear())) {
                throw new BusinessRuleException(course.getCode() + " - " + course.getName() + " is not available for Year "
                        + r.academicYear() + " " + r.specialisation().getName() + " students.");
            }
        }
    }
}
