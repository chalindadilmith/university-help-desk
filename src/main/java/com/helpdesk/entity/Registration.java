package com.helpdesk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Table: registrations
 * A student's registration for a given semester (UC-08). One registration
 * per student per semester (unique constraint). Submitted as PENDING and
 * approved/rejected by an Admin. No payment entity linked - Phase 0
 * decision removed the Payment Gateway from scope.
 *
 * All columns added for the approval workflow are nullable so Hibernate's
 * ddl-auto=update can add them to a table that already contains rows.
 */
@Entity
@Table(name = "registrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_registration_student_semester", columnNames = {"student_id", "semester_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique, human-readable registration number, e.g. SR2026-000045. */
    @Column(name = "reference_no", length = 30)
    private String referenceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialisation_id")
    private DegreeSpecialisation specialisation;

    /** Academic year of study (1-4). */
    @Column(name = "academic_year")
    private Integer academicYear;

    /** Semester within the academic year (1 or 2). */
    @Column(name = "semester_of_study")
    private Integer semesterOfStudy;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(name = "total_credits")
    private Integer totalCredits;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.PENDING;

    /** Selected course offerings (M-M, join table registration_courses). */
    @ManyToMany
    @JoinTable(
            name = "registration_courses",
            joinColumns = @JoinColumn(name = "registration_id"),
            inverseJoinColumns = @JoinColumn(name = "course_offering_id")
    )
    @Builder.Default
    private Set<CourseOffering> courseOfferings = new HashSet<>();

    /** Admin's note when approving/rejecting (required on reject). */
    @Column(name = "admin_remarks", length = 500)
    private String adminRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
