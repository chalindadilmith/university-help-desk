package com.helpdesk.service.impl;

import com.helpdesk.dto.CourseForm;
import com.helpdesk.dto.OfferingForm;
import com.helpdesk.dto.SemesterForm;
import com.helpdesk.entity.*;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.*;
import com.helpdesk.service.NotificationService;
import com.helpdesk.service.RegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * F6: semester registration only - NO payment step (Phase 0 decision: the
 * Payment Gateway was explicitly removed from scope). Schedule-conflict
 * detection is simplified to a credit-limit check only, since Course /
 * CourseOffering don't model structured day/time data in this MVP - see
 * README for this documented simplification.
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    private static final int MAX_CREDITS_PER_SEMESTER = 18;

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final RegistrationRepository registrationRepository;
    private final NotificationService notificationService;

    public RegistrationServiceImpl(SemesterRepository semesterRepository, CourseRepository courseRepository,
                                    CourseOfferingRepository offeringRepository, RegistrationRepository registrationRepository,
                                    NotificationService notificationService) {
        this.semesterRepository = semesterRepository;
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.registrationRepository = registrationRepository;
        this.notificationService = notificationService;
    }

    @Override
    public List<Semester> listSemesters() {
        return semesterRepository.findAllByOrderByStartDateDesc();
    }

    @Override
    @Transactional
    public Semester createSemester(SemesterForm form) {
        Semester semester = Semester.builder()
                .name(form.getName())
                .startDate(form.getStartDate())
                .endDate(form.getEndDate())
                .registrationOpen(form.isRegistrationOpen())
                .build();
        return semesterRepository.save(semester);
    }

    @Override
    public List<Course> listCourses() {
        return courseRepository.findAllByOrderByCodeAsc();
    }

    @Override
    @Transactional
    public Course createCourse(CourseForm form) {
        if (courseRepository.existsByCodeIgnoreCase(form.getCode())) {
            throw new DuplicateResourceException("code", "A course with this code already exists.");
        }
        Course course = Course.builder()
                .code(form.getCode())
                .name(form.getName())
                .credits(form.getCredits())
                .build();
        return courseRepository.save(course);
    }

    @Override
    public List<CourseOffering> listOfferings(Long semesterId) {
        return offeringRepository.findBySemesterIdOrderByCourseCodeAsc(semesterId);
    }

    @Override
    @Transactional
    public CourseOffering createOffering(OfferingForm form) {
        Semester semester = semesterRepository.findById(form.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + form.getSemesterId()));
        Course course = courseRepository.findById(form.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + form.getCourseId()));

        CourseOffering offering = CourseOffering.builder()
                .semester(semester)
                .course(course)
                .capacity(form.getCapacity())
                .scheduleInfo(form.getScheduleInfo())
                .build();
        return offeringRepository.save(offering);
    }

    @Override
    public List<Semester> openSemesters() {
        return semesterRepository.findByRegistrationOpenTrue();
    }

    @Override
    public Optional<Registration> myRegistration(Long studentId, Long semesterId) {
        return registrationRepository.findByStudentIdAndSemesterId(studentId, semesterId);
    }

    @Override
    @Transactional
    public Registration register(User student, Long semesterId, List<Long> offeringIds) {
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + semesterId));

        if (!semester.isRegistrationOpen()) {
            throw new BusinessRuleException("Registration is closed for " + semester.getName() + ".");
        }
        if (offeringIds == null || offeringIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one course.");
        }

        Set<CourseOffering> offerings = new HashSet<>();
        int totalCredits = 0;
        for (Long offeringId : offeringIds) {
            CourseOffering offering = offeringRepository.findById(offeringId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));
            offerings.add(offering);
            totalCredits += offering.getCourse().getCredits();
        }

        if (totalCredits > MAX_CREDITS_PER_SEMESTER) {
            throw new BusinessRuleException("Selected courses total " + totalCredits +
                    " credits, which exceeds the " + MAX_CREDITS_PER_SEMESTER + "-credit limit per semester. Remove a course and try again.");
        }

        Registration registration = registrationRepository.findByStudentIdAndSemesterId(student.getId(), semesterId)
                .orElse(Registration.builder().student(student).semester(semester).build());

        registration.setCourseOfferings(offerings);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        Registration saved = registrationRepository.save(registration);

        notificationService.notify(student, "REGISTRATION_CONFIRMED",
                "Your registration for " + semester.getName() + " is confirmed (" + totalCredits + " credits).",
                "Registration", saved.getId());

        return saved;
    }

    @Override
    public List<Registration> myRegistrations(Long studentId) {
        return registrationRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }
}
