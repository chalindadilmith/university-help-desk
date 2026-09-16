package com.helpdesk.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OfferingForm {

    @NotNull(message = "Select a semester")
    private Long semesterId;

    @NotNull(message = "Select a course")
    private Long courseId;

    @NotNull(message = "Capacity is required")
    @Positive
    private Integer capacity;

    private String scheduleInfo;
}
