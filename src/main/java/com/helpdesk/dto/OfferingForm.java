package com.helpdesk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Add / edit a module offering (a module offered in a specific semester). */
@Getter
@Setter
@NoArgsConstructor
public class OfferingForm {

    @NotNull(message = "Select a semester")
    private Long semesterId;

    @NotNull(message = "Select a module")
    private Long courseId;

    @NotNull(message = "Capacity is required")
    @Positive(message = "Capacity must be greater than 0")
    @Max(value = 1000, message = "Capacity must be 1000 or fewer")
    private Integer capacity;

    @Size(max = 255, message = "Schedule must be 255 characters or fewer")
    private String scheduleInfo;
}
