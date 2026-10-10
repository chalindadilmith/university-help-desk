package com.helpdesk.service;

import com.helpdesk.dto.EligibilityResult;
import com.helpdesk.dto.SemesterForm;
import com.helpdesk.dto.SemesterRegistrationForm;
import com.helpdesk.dto.SpecialisationForm;
import com.helpdesk.entity.*;

import java.util.List;
import java.util.Optional;

/**
 * F6 - Semester registration (UC-08): semesters and specialisations set up by
 * the Admin, eligibility check + registration form for the Student, and the
 * Admin approval step. Module/offering CRUD lives in ModuleOfferingService.
 */
public interface RegistrationService {

    int MAX_CREDITS_PER_SEMESTER = 18;

    // ---- Admin setup ----
    List<Semester> listSemesters();
    Semester getSemester(Long id);
    Semester createSemester(SemesterForm form, User actingAdmin);
    Semester toggleRegistrationOpen(Long semesterId, User actingAdmin);

    List<DegreeSpecialisation> listSpecialisations();
    List<DegreeSpecialisation> activeSpecialisations();
    DegreeSpecialisation createSpecialisation(SpecialisationForm form, User actingAdmin);
    DegreeSpecialisation toggleSpecialisationActive(Long id, User actingAdmin);

    // ---- Student flow ----
    List<Semester> openSemesters();
    Optional<Registration> myRegistration(Long studentId, Long semesterId);
    List<Registration> myRegistrations(Long studentId);

    /** Runs every eligibility rule for this student and semester (nothing is saved). */
    EligibilityResult checkEligibility(User student, Semester semester);

    /**
     * Submits (or re-submits) the registration form. Saved as PENDING until an Admin reviews it.
     * @throws com.helpdesk.exception.BusinessRuleException if not eligible or the module selection is invalid.
     */
    Registration submit(User student, SemesterRegistrationForm form);

    /** Student withdraws their own PENDING registration. */
    void cancel(User student, Long registrationId);

    // ---- Admin approval ----
    List<Registration> search(Long semesterId, RegistrationStatus status);
    Registration getRegistration(Long id);
    long countPending();
    Registration approve(Long registrationId, String remarks, User actingAdmin);
    Registration reject(Long registrationId, String remarks, User actingAdmin);
}
