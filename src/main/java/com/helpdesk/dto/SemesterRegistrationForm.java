package com.helpdesk.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Form-backing object for the student's semester registration form.
 * Name, email, university ID and student ID are NOT on this form - they are
 * read from the logged-in account, so a student can't register as someone else.
 */
@Getter
@Setter
@NoArgsConstructor
public class SemesterRegistrationForm {

    @NotNull(message = "Select a semester")
    private Long semesterId;

    @NotNull(message = "Select your degree specialisation")
    private Long specialisationId;

    @NotNull(message = "Select your academic year")
    @Min(value = 1, message = "Academic year must be between 1 and 4")
    @Max(value = 4, message = "Academic year must be between 1 and 4")
    private Integer academicYear;

    @NotNull(message = "Select your semester of study")
    @Min(value = 1, message = "Semester must be 1 or 2")
    @Max(value = 2, message = "Semester must be 1 or 2")
    private Integer semesterOfStudy;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^(\\+94|0)\\d{9}$", message = "Enter a valid phone number, e.g. 0771234567 or +94771234567")
    private String contactNumber;

    @NotEmpty(message = "Select at least one module")
    private List<Long> offeringIds = new ArrayList<>();

    @AssertTrue(message = "You must confirm the declaration")
    private boolean declaration;
}
