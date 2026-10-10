package com.helpdesk.strategy.registration;

import com.helpdesk.entity.CourseOffering;
import com.helpdesk.entity.DegreeSpecialisation;
import com.helpdesk.entity.Semester;

import java.util.List;

/** Everything a rule may need to judge a semester registration. */
public record RegistrationRequest(Semester semester,
                                  DegreeSpecialisation specialisation,
                                  Integer academicYear,
                                  Long excludeRegistrationId,
                                  List<CourseOffering> offerings) {

    public int totalCredits() {
        return offerings.stream().mapToInt(o -> o.getCourse().getCredits()).sum();
    }
}
