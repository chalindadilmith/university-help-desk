package com.helpdesk.service.impl;

import com.helpdesk.dto.CourseForm;
import com.helpdesk.dto.OfferingForm;
import com.helpdesk.entity.*;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.*;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.ModuleOfferingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModuleOfferingServiceImpl implements ModuleOfferingService {

    public static final List<RegistrationStatus> SEAT_HOLDING = List.of(RegistrationStatus.PENDING, RegistrationStatus.APPROVED);

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final SemesterRepository semesterRepository;
    private final DegreeSpecialisationRepository specialisationRepository;
    private final RegistrationRepository registrationRepository;
    private final AuditLogService auditLogService;

    public ModuleOfferingServiceImpl(CourseRepository courseRepository, CourseOfferingRepository offeringRepository,
                                     SemesterRepository semesterRepository,
                                     DegreeSpecialisationRepository specialisationRepository,
                                     RegistrationRepository registrationRepository, AuditLogService auditLogService) {
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.semesterRepository = semesterRepository;
        this.specialisationRepository = specialisationRepository;
        this.registrationRepository = registrationRepository;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ modules

    @Override
    public List<Course> listCourses() {
        return courseRepository.findAllByOrderByCodeAsc();
    }

    @Override
    public Course getCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + id));
    }

    @Override
    @Transactional
    public Course createCourse(CourseForm form, User actingAdmin) {
        String code = form.getCode().trim().toUpperCase();
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("code", "A module with code " + code + " already exists.");
        }
        Course course = new Course();
        applyCourseForm(course, form, code);
        Course saved = courseRepository.save(course);
        auditLogService.log(actingAdmin, "CREATE_MODULE", "Course", saved.getId(), saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public Course updateCourse(Long id, CourseForm form, User actingAdmin) {
        Course course = getCourse(id);
        String code = form.getCode().trim().toUpperCase();
        if (courseRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("code", "A module with code " + code + " already exists.");
        }
        applyCourseForm(course, form, code);
        auditLogService.log(actingAdmin, "UPDATE_MODULE", "Course", id, course.getCode());
        return course;
    }

    @Override
    @Transactional
    public void deleteCourse(Long id, User actingAdmin) {
        Course course = getCourse(id);
        if (offeringRepository.existsByCourseId(id)) {
            throw new BusinessRuleException("Module " + course.getCode() +
                    " is still offered in a semester. Delete its offerings first.");
        }
        courseRepository.delete(course);
        auditLogService.log(actingAdmin, "DELETE_MODULE", "Course", id, course.getCode());
    }

    private void applyCourseForm(Course course, CourseForm form, String code) {
        course.setCode(code);
        course.setName(form.getName().trim());
        course.setCredits(form.getCredits());
        course.setYearLevel(form.getYearLevel());
        course.setSpecialisation(form.getSpecialisationId() == null ? null :
                specialisationRepository.findById(form.getSpecialisationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Specialisation not found: " + form.getSpecialisationId())));
    }

    // ---------------------------------------------------------------- offerings

    @Override
    public List<CourseOffering> listOfferings(Long semesterId) {
        return semesterId == null ? List.of() : offeringRepository.findBySemesterIdOrderByCourseCodeAsc(semesterId);
    }

    @Override
    public CourseOffering getOffering(Long id) {
        return offeringRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module offering not found: " + id));
    }

    @Override
    @Transactional
    public CourseOffering createOffering(OfferingForm form, User actingAdmin) {
        if (offeringRepository.existsBySemesterIdAndCourseId(form.getSemesterId(), form.getCourseId())) {
            throw new DuplicateResourceException("courseId", "This module is already offered in that semester.");
        }
        CourseOffering offering = new CourseOffering();
        applyOfferingForm(offering, form);
        CourseOffering saved = offeringRepository.save(offering);
        auditLogService.log(actingAdmin, "CREATE_OFFERING", "CourseOffering", saved.getId(),
                saved.getCourse().getCode() + " in " + saved.getSemester().getName());
        return saved;
    }

    @Override
    @Transactional
    public CourseOffering updateOffering(Long id, OfferingForm form, User actingAdmin) {
        CourseOffering offering = getOffering(id);
        if (offeringRepository.existsBySemesterIdAndCourseIdAndIdNot(form.getSemesterId(), form.getCourseId(), id)) {
            throw new DuplicateResourceException("courseId", "This module is already offered in that semester.");
        }
        boolean hasRegistrations = registrationRepository.existsByCourseOfferings_Id(id);
        if (hasRegistrations && (!offering.getSemester().getId().equals(form.getSemesterId())
                || !offering.getCourse().getId().equals(form.getCourseId()))) {
            throw new BusinessRuleException("Students have already registered for this offering, so its semester " +
                    "and module can't be changed. You can still change the capacity and schedule.");
        }
        long taken = registrationRepository.countSeatsTaken(id, SEAT_HOLDING, null);
        if (form.getCapacity() < taken) {
            throw new BusinessRuleException("Capacity can't be lower than the " + taken + " seat(s) already taken.");
        }
        applyOfferingForm(offering, form);
        auditLogService.log(actingAdmin, "UPDATE_OFFERING", "CourseOffering", id,
                offering.getCourse().getCode() + " capacity=" + offering.getCapacity());
        return offering;
    }

    @Override
    @Transactional
    public void deleteOffering(Long id, User actingAdmin) {
        CourseOffering offering = getOffering(id);
        if (registrationRepository.existsByCourseOfferings_Id(id)) {
            throw new BusinessRuleException("Students have registered for " + offering.getCourse().getCode() +
                    " in " + offering.getSemester().getName() + ", so it can't be deleted.");
        }
        String label = offering.getCourse().getCode() + " in " + offering.getSemester().getName();
        offeringRepository.delete(offering);
        auditLogService.log(actingAdmin, "DELETE_OFFERING", "CourseOffering", id, label);
    }

    private void applyOfferingForm(CourseOffering offering, OfferingForm form) {
        offering.setSemester(semesterRepository.findById(form.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + form.getSemesterId())));
        offering.setCourse(courseRepository.findById(form.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + form.getCourseId())));
        offering.setCapacity(form.getCapacity());
        offering.setScheduleInfo(form.getScheduleInfo() == null || form.getScheduleInfo().isBlank()
                ? null : form.getScheduleInfo().trim());
    }

    @Override
    public Map<Long, Long> seatsTaken(List<CourseOffering> offerings) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (CourseOffering o : offerings) {
            result.put(o.getId(), registrationRepository.countSeatsTaken(o.getId(), SEAT_HOLDING, null));
        }
        return result;
    }

    // ---------------------------------------------------------------- mapping

    @Override
    public CourseForm toForm(Course course) {
        CourseForm form = new CourseForm();
        form.setCode(course.getCode());
        form.setName(course.getName());
        form.setCredits(course.getCredits());
        form.setYearLevel(course.getYearLevel());
        form.setSpecialisationId(course.getSpecialisation() == null ? null : course.getSpecialisation().getId());
        return form;
    }

    @Override
    public OfferingForm toForm(CourseOffering offering) {
        OfferingForm form = new OfferingForm();
        form.setSemesterId(offering.getSemester().getId());
        form.setCourseId(offering.getCourse().getId());
        form.setCapacity(offering.getCapacity());
        form.setScheduleInfo(offering.getScheduleInfo());
        return form;
    }
}
