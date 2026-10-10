package com.helpdesk.strategy.registration;

import com.helpdesk.entity.CourseOffering;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.util.ScheduleParser;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** ConcreteStrategy: two selected modules must not be taught at the same time. */
@Component
@Order(40)
public class ScheduleClashRule implements RegistrationRule {

    @Override
    public void validate(RegistrationRequest r) {
        List<CourseOffering> chosen = r.offerings();
        for (int i = 0; i < chosen.size(); i++) {
            for (int j = i + 1; j < chosen.size(); j++) {
                ScheduleParser.Slot clash = ScheduleParser.firstClash(
                        chosen.get(i).getScheduleInfo(), chosen.get(j).getScheduleInfo());
                if (clash != null) {
                    throw new BusinessRuleException(chosen.get(i).getCourse().getCode() + " and "
                            + chosen.get(j).getCourse().getCode() + " overlap on " + clash.describe()
                            + ". Remove one of them and try again.");
                }
            }
        }
    }
}
