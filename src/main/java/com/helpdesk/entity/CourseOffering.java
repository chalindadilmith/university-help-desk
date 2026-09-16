package com.helpdesk.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Table: course_offerings
 * A specific course offered in a specific semester (needed to check capacity
 * and schedule conflicts per UC-08 step 6). Unique per (semester, course).
 *
 * equals/hashCode are based on id only (not Lombok's default field-by-field,
 * which would drag in the Course/Semester associations and risk lazy-loading
 * recursion). This matters because Registration.courseOfferings is a Set,
 * and a Set.contains() check across entities loaded in two separate
 * repository calls (e.g. in RegistrationController, to pre-check "already
 * selected" boxes) would otherwise always be false - the JVM's default
 * identity equals only matches when it's literally the same object instance.
 */
@Entity
@Table(name = "course_offerings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_offering_semester_course", columnNames = {"semester_id", "course_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @NotNull
    @Positive
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "schedule_info", length = 255)
    private String scheduleInfo;
}
