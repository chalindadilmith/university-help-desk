package com.helpdesk.strategy.registration;

import com.helpdesk.entity.CourseOffering;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.repository.RegistrationRepository;
import com.helpdesk.service.impl.ModuleOfferingServiceImpl;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** ConcreteStrategy: a module that is already full cannot be chosen. */
@Component
@Order(30)
public class SeatCapacityRule implements RegistrationRule {

    private final RegistrationRepository registrationRepository;

    public SeatCapacityRule(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    @Override
    public void validate(RegistrationRequest r) {
        for (CourseOffering o : r.offerings()) {
            long taken = registrationRepository.countSeatsTaken(
                    o.getId(), ModuleOfferingServiceImpl.SEAT_HOLDING, r.excludeRegistrationId());
            if (taken >= o.getCapacity()) {
                throw new BusinessRuleException(o.getCourse().getCode() + " is full (" + o.getCapacity()
                        + " seats). Choose another module.");
            }
        }
    }
}
