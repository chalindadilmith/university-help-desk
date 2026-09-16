package com.helpdesk.service;

import com.helpdesk.dto.CourseForm;
import com.helpdesk.dto.OfferingForm;
import com.helpdesk.dto.SemesterForm;
import com.helpdesk.entity.*;

import java.util.List;
import java.util.Optional;

public interface RegistrationService {

    // ---- Admin setup (UC-08 precondition: catalog must exist) ----
    List<Semester> listSemesters();
    Semester createSemester(SemesterForm form);

    List<Course> listCourses();
    Course createCourse(CourseForm form);

    List<CourseOffering> listOfferings(Long semesterId);
    CourseOffering createOffering(OfferingForm form);

    // ---- Student flow ----
    List<Semester> openSemesters();
    Optional<Registration> myRegistration(Long studentId, Long semesterId);

    /** @throws com.helpdesk.exception.BusinessRuleException on schedule/credit-limit violations. */
    Registration register(User student, Long semesterId, List<Long> offeringIds);

    List<Registration> myRegistrations(Long studentId);
}
