package com.helpdesk.dto;

import com.helpdesk.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student correcting their own ticket (subject, description, category, urgency). */
@Getter
@Setter
@NoArgsConstructor
public class TicketEditForm {

    @NotNull(message = "Select a category")
    private Long categoryId;

    @NotBlank(message = "Subject is required")
    @Size(max = 150, message = "Subject must be 150 characters or fewer")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be under 2000 characters")
    private String description;

    private Priority priority = Priority.MEDIUM;
}
