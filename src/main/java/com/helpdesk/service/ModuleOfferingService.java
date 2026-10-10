package com.helpdesk.service;

import com.helpdesk.dto.CourseForm;
import com.helpdesk.dto.OfferingForm;
import com.helpdesk.entity.Course;
import com.helpdesk.entity.CourseOffering;
import com.helpdesk.entity.User;

import java.util.List;
import java.util.Map;

/**
 * Admin management of the module catalog (courses) and the modules offered
 * in each semester (course offerings): add, update and delete.
 */
public interface ModuleOfferingService {

    // ---- Modules (catalog) ----
    List<Course> listCourses();
    Course getCourse(Long id);
    Course createCourse(CourseForm form, User actingAdmin);
    Course updateCourse(Long id, CourseForm form, User actingAdmin);

    /** @throws com.helpdesk.exception.BusinessRuleException if the module is still offered in a semester. */
    void deleteCourse(Long id, User actingAdmin);

    // ---- Offerings (module offered in a semester) ----
    List<CourseOffering> listOfferings(Long semesterId);
    CourseOffering getOffering(Long id);
    CourseOffering createOffering(OfferingForm form, User actingAdmin);
    CourseOffering updateOffering(Long id, OfferingForm form, User actingAdmin);

    /** @throws com.helpdesk.exception.BusinessRuleException if students have registered for it. */
    void deleteOffering(Long id, User actingAdmin);

    /** offeringId -> seats taken by PENDING + APPROVED registrations. */
    Map<Long, Long> seatsTaken(List<CourseOffering> offerings);

    CourseForm toForm(Course course);
    OfferingForm toForm(CourseOffering offering);
}
