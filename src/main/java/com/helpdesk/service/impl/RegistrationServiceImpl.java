package com.helpdesk.service.impl;

import com.helpdesk.dto.EligibilityResult;
import com.helpdesk.dto.SemesterForm;
import com.helpdesk.dto.SemesterRegistrationForm;
import com.helpdesk.dto.SpecialisationForm;
import com.helpdesk.entity.*;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.*;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.NotificationService;
import com.helpdesk.service.RegistrationService;
import com.helpdesk.util.IdGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * F6: semester registration with eligibility check and Admin approval.
 * NO payment step (Phase 0 decision: the Payment Gateway was removed from scope).
 * Schedule-conflict detection is still simplified to a credit-limit check,
 * since offerings only store a free-text schedule (see README).
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    /** Kept below the 500-char notification message column once the prefix text is added. */
    private static final int MAX_REMARKS_LENGTH = 400;

    private final SemesterRepository semesterRepository;
    private final CourseOfferingRepository offeringRepository;
    private final RegistrationRepository registrationRepository;
    private final DegreeSpecialisationRepository specialisationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    /** Strategy pattern: runs every registration rule (semester, availability, seats, clash, credits). */
    private final com.helpdesk.strategy.registration.RegistrationValidator registrationValidator;

    public RegistrationServiceImpl(SemesterRepository semesterRepository, CourseOfferingRepository offeringRepository,
                                   RegistrationRepository registrationRepository,
                                   DegreeSpecialisationRepository specialisationRepository,
                                   UserRepository userRepository, NotificationService notificationService,
                                   AuditLogService auditLogService,
                                   com.helpdesk.strategy.registration.RegistrationValidator registrationValidator) {
        this.registrationValidator = registrationValidator;
        this.semesterRepository = semesterRepository;
        this.offeringRepository = offeringRepository;
        this.registrationRepository = registrationRepository;
        this.specialisationRepository = specialisationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    // ============================================================ admin setup

    @Override
    public List<Semester> listSemesters() {
        return semesterRepository.findAllByOrderByStartDateDesc();
    }

    @Override
    public Semester getSemester(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + id));
    }

    @Override
    @Transactional
    public Semester createSemester(SemesterForm form, User actingAdmin) {
        if (form.getEndDate().isBefore(form.getStartDate())) {
            throw new BusinessRuleException("End date must be on or after the start date.");
        }
        boolean nameTaken = semesterRepository.findAll().stream()
                .anyMatch(s -> s.getName().equalsIgnoreCase(form.getName().trim()));
        if (nameTaken) {
            throw new DuplicateResourceException("name", "A semester called '" + form.getName().trim() + "' already exists.");
        }
        Semester saved = semesterRepository.save(Semester.builder()
                .name(form.getName().trim())
                .startDate(form.getStartDate())
                .endDate(form.getEndDate())
                .registrationOpen(form.isRegistrationOpen())
                .build());
        auditLogService.log(actingAdmin, "CREATE_SEMESTER", "Semester", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    @Transactional
    public Semester toggleRegistrationOpen(Long semesterId, User actingAdmin) {
        Semester semester = getSemester(semesterId);
        semester.setRegistrationOpen(!semester.isRegistrationOpen());
        auditLogService.log(actingAdmin, semester.isRegistrationOpen() ? "OPEN_REGISTRATION" : "CLOSE_REGISTRATION",
                "Semester", semesterId, semester.getName());
        return semester;
    }

    @Override
    public List<DegreeSpecialisation> listSpecialisations() {
        return specialisationRepository.findAllByOrderByNameAsc();
    }

    @Override
    public List<DegreeSpecialisation> activeSpecialisations() {
        return specialisationRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional
    public DegreeSpecialisation createSpecialisation(SpecialisationForm form, User actingAdmin) {
        String code = form.getCode().trim().toUpperCase();
        if (specialisationRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("code", "A specialisation with code " + code + " already exists.");
        }
        if (specialisationRepository.existsByNameIgnoreCase(form.getName().trim())) {
            throw new DuplicateResourceException("name", "A specialisation with this name already exists.");
        }
        DegreeSpecialisation saved = specialisationRepository.save(DegreeSpecialisation.builder()
                .code(code)
                .name(form.getName().trim())
                .description(form.getDescription())
                .active(true)
                .build());
        auditLogService.log(actingAdmin, "CREATE_SPECIALISATION", "DegreeSpecialisation", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    @Transactional
    public DegreeSpecialisation toggleSpecialisationActive(Long id, User actingAdmin) {
        DegreeSpecialisation spec = specialisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialisation not found: " + id));
        spec.setActive(!spec.isActive());
        auditLogService.log(actingAdmin, spec.isActive() ? "ACTIVATE_SPECIALISATION" : "DEACTIVATE_SPECIALISATION",
                "DegreeSpecialisation", id, spec.getName());
        return spec;
    }

    // ============================================================ student flow

    @Override
    public List<Semester> openSemesters() {
        return semesterRepository.findByRegistrationOpenTrue();
    }

    @Override
    public Optional<Registration> myRegistration(Long studentId, Long semesterId) {
        return registrationRepository.findByStudentIdAndSemesterId(studentId, semesterId);
    }

    @Override
    public List<Registration> myRegistrations(Long studentId) {
        return registrationRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    @Override
    public EligibilityResult checkEligibility(User student, Semester semester) {
        // Re-read the account so a deactivation since login is picked up.
        User fresh = userRepository.findById(student.getId()).orElse(student);
        EligibilityResult result = new EligibilityResult();

        boolean active = fresh.getStatus() == AccountStatus.ACTIVE && fresh.getRole() == Role.STUDENT;
        result.add("Active student account", active,
                active ? "Your student account is active." : "Only active student accounts can register.");

        boolean hasId = fresh.getStudentCode() != null;
        result.add("Student ID assigned", hasId,
                hasId ? "Your student ID is " + fresh.getStudentCode() + "."
                      : "Your account has no student ID yet. Please contact the help desk.");

        boolean windowOpen = semester.isRegistrationOpen() && !LocalDate.now().isAfter(semester.getEndDate());
        result.add("Registration period open", windowOpen,
                windowOpen ? "Registration for " + semester.getName() + " is open."
                           : "Registration for " + semester.getName() + " is closed.");

        Optional<Registration> existing = registrationRepository.findByStudentIdAndSemesterId(fresh.getId(), semester.getId());
        boolean notApproved = existing.map(r -> r.getStatus() != RegistrationStatus.APPROVED).orElse(true);
        String existingDetail;
        if (existing.isEmpty()) {
            existingDetail = "You have not registered for this semester yet.";
        } else {
            existingDetail = switch (existing.get().getStatus()) {
                case APPROVED -> "Your registration " + existing.get().getReferenceNo() + " is already approved.";
                case PENDING -> "Your registration is awaiting approval - submitting again will update it.";
                case REJECTED -> "Your previous registration was rejected - you can correct it and resubmit.";
                case CANCELLED -> "You cancelled your previous registration - you can submit a new one.";
            };
        }
        result.add("Not already registered", notApproved, existingDetail);

        boolean hasModules = !offeringRepository.findBySemesterIdOrderByCourseCodeAsc(semester.getId()).isEmpty();
        result.add("Modules offered", hasModules,
                hasModules ? "Modules are available for this semester." : "No modules have been offered for this semester yet.");

        return result;
    }

    @Override
    @Transactional
    public Registration submit(User student, SemesterRegistrationForm form) {
        Semester semester = getSemester(form.getSemesterId());

        EligibilityResult eligibility = checkEligibility(student, semester);
        if (!eligibility.isEligible()) {
            throw new BusinessRuleException(eligibility.getFirstFailure());
        }

        DegreeSpecialisation specialisation = specialisationRepository.findById(form.getSpecialisationId())
                .filter(DegreeSpecialisation::isActive)
                .orElseThrow(() -> new BusinessRuleException("Select a valid degree specialisation."));

        Optional<Registration> existing = registrationRepository.findByStudentIdAndSemesterId(student.getId(), semester.getId());
        Long excludeId = existing.map(Registration::getId).orElse(null);

        Set<CourseOffering> offerings = new LinkedHashSet<>();
        for (Long offeringId : new LinkedHashSet<>(form.getOfferingIds())) {
            offerings.add(offeringRepository.findById(offeringId)
                    .orElseThrow(() -> new BusinessRuleException("A selected module no longer exists. Please reload the page.")));
        }

        // Strategy pattern: every rule checks the selection; the first one that fails throws a friendly message
        com.helpdesk.strategy.registration.RegistrationRequest request = new com.helpdesk.strategy.registration.RegistrationRequest(
                semester, specialisation, form.getAcademicYear(), excludeId, new java.util.ArrayList<>(offerings));
        registrationValidator.validate(request);
        int totalCredits = request.totalCredits();

        Registration registration = existing.orElseGet(() ->
                Registration.builder().student(student).semester(semester).build());
        registration.setSpecialisation(specialisation);
        registration.setAcademicYear(form.getAcademicYear());
        registration.setSemesterOfStudy(form.getSemesterOfStudy());
        registration.setContactNumber(form.getContactNumber().trim());
        registration.setCourseOfferings(new HashSet<>(offerings));
        registration.setTotalCredits(totalCredits);
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setAdminRemarks(null);
        registration.setReviewedBy(null);
        registration.setReviewedAt(null);

        Registration saved = registrationRepository.save(registration);
        if (saved.getReferenceNo() == null) {
            saved.setReferenceNo(IdGenerator.registrationReference(saved.getId(), saved.getCreatedAt()));
        }

        notificationService.notify(student, "REGISTRATION_SUBMITTED",
                "Your registration " + saved.getReferenceNo() + " for " + semester.getName() +
                        " was submitted and is awaiting admin approval.", "Registration", saved.getId());
        for (User admin : userRepository.findAll()) {
            if (admin.getRole() == Role.ADMIN && admin.getStatus() == AccountStatus.ACTIVE) {
                notificationService.notify(admin, "REGISTRATION_PENDING",
                        "New semester registration " + saved.getReferenceNo() + " from " + student.getFullName() +
                                " needs approval.", "Registration", saved.getId());
            }
        }
        return saved;
    }

    @Override
    @Transactional
    public void cancel(User student, Long registrationId) {
        Registration registration = getRegistration(registrationId);
        if (!registration.getStudent().getId().equals(student.getId())) {
            throw new AccessDeniedException("You can only cancel your own registration.");
        }
        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessRuleException("Only a registration that is still pending can be cancelled.");
        }
        registration.setStatus(RegistrationStatus.CANCELLED);
    }

    // ============================================================ admin approval

    @Override
    public List<Registration> search(Long semesterId, RegistrationStatus status) {
        return registrationRepository.search(semesterId, status);
    }

    @Override
    public Registration getRegistration(Long id) {
        return registrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + id));
    }

    @Override
    public long countPending() {
        return registrationRepository.countByStatus(RegistrationStatus.PENDING);
    }

    @Override
    @Transactional
    public Registration approve(Long registrationId, String remarks, User actingAdmin) {
        Registration registration = requirePending(registrationId);

        // Seats are reserved at submit time, but re-check against APPROVED
        // registrations in case the admin lowered capacity since then.
        for (CourseOffering offering : registration.getCourseOfferings()) {
            long approved = registrationRepository.countSeatsTaken(offering.getId(),
                    List.of(RegistrationStatus.APPROVED), registration.getId());
            if (approved >= offering.getCapacity()) {
                throw new BusinessRuleException(offering.getCourse().getCode() + " has no approved seats left (capacity " +
                        offering.getCapacity() + "). Increase the capacity or reject this registration.");
            }
        }

        markReviewed(registration, RegistrationStatus.APPROVED, remarks, actingAdmin);
        auditLogService.log(actingAdmin, "APPROVE_REGISTRATION", "Registration", registration.getId(), registration.getReferenceNo());
        notificationService.notify(registration.getStudent(), "REGISTRATION_APPROVED",
                "Your registration " + registration.getReferenceNo() + " for " + registration.getSemester().getName() +
                        " has been approved.", "Registration", registration.getId());
        return registration;
    }

    @Override
    @Transactional
    public Registration reject(Long registrationId, String remarks, User actingAdmin) {
        if (remarks == null || remarks.isBlank()) {
            throw new BusinessRuleException("Please enter a reason for rejecting this registration.");
        }
        Registration registration = requirePending(registrationId);
        markReviewed(registration, RegistrationStatus.REJECTED, remarks, actingAdmin);
        auditLogService.log(actingAdmin, "REJECT_REGISTRATION", "Registration", registration.getId(),
                registration.getReferenceNo() + ": " + remarks.trim());
        notificationService.notify(registration.getStudent(), "REGISTRATION_REJECTED",
                "Your registration " + registration.getReferenceNo() + " was rejected: " + remarks.trim(),
                "Registration", registration.getId());
        return registration;
    }

    private Registration requirePending(Long registrationId) {
        Registration registration = getRegistration(registrationId);
        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessRuleException("Registration " + registration.getReferenceNo() + " is already " +
                    registration.getStatus().name().toLowerCase() + ".");
        }
        return registration;
    }

    private void markReviewed(Registration registration, RegistrationStatus status, String remarks, User admin) {
        if (remarks != null && remarks.trim().length() > MAX_REMARKS_LENGTH) {
            throw new BusinessRuleException("Remarks must be " + MAX_REMARKS_LENGTH + " characters or fewer.");
        }
        registration.setStatus(status);
        registration.setAdminRemarks(remarks == null || remarks.isBlank() ? null : remarks.trim());
        registration.setReviewedBy(admin);
        registration.setReviewedAt(LocalDateTime.now());
    }
}
