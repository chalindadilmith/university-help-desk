package com.helpdesk.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Table: courses - the catalog of modules that can be offered in a semester.
 *
 * yearLevel and specialisation drive the registration eligibility check:
 *  - yearLevel null      -> module is open to every academic year
 *  - specialisation null -> common module, open to every specialisation
 * (Both columns are nullable on purpose so existing rows keep working and
 * Hibernate's ddl-auto=update can add them to a table that already has data.)
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotNull
    @Positive
    @Column(name = "credits", nullable = false)
    private Integer credits;

    /** Academic year (1-4) the module belongs to. */
    @Min(1)
    @Max(4)
    @Column(name = "year_level")
    private Integer yearLevel;

    /** Null = common module for all specialisations. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialisation_id")
    private DegreeSpecialisation specialisation;

    /** True when this module is available to a student in the given specialisation/year. */
    public boolean isAvailableFor(Long specialisationId, Integer academicYear) {
        boolean specOk = specialisation == null
                || (specialisationId != null && specialisation.getId().equals(specialisationId));
        boolean yearOk = yearLevel == null || (academicYear != null && yearLevel.equals(academicYear));
        return specOk && yearOk;
    }
}
