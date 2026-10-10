package com.helpdesk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Add / edit a module (course) in the catalog. */
@Getter
@Setter
@NoArgsConstructor
public class CourseForm {

    @NotBlank(message = "Module code is required")
    @Size(max = 20, message = "Module code must be 20 characters or fewer")
    private String code;

    @NotBlank(message = "Module name is required")
    @Size(max = 150, message = "Module name must be 150 characters or fewer")
    private String name;

    @NotNull(message = "Credits are required")
    @Positive(message = "Credits must be greater than 0")
    @Max(value = 10, message = "Credits must be 10 or fewer")
    private Integer credits;

    @NotNull(message = "Select the academic year")
    @Min(value = 1, message = "Year must be between 1 and 4")
    @Max(value = 4, message = "Year must be between 1 and 4")
    private Integer yearLevel;

    /** Empty = common module for every specialisation. */
    private Long specialisationId;
}
