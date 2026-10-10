package com.helpdesk.dto;

import com.helpdesk.entity.Priority;
import com.helpdesk.entity.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create / edit form used by Admin and Staff to manage any ticket. {@code studentId} is only used
 * when creating a ticket on a student's behalf; {@code assignedStaffId} is only honoured for Admin.
 */
@Getter
@Setter
@NoArgsConstructor
public class ManagedTicketForm {

    /** The student the ticket belongs to (create only). */
    private Long studentId;

    @NotNull(message = "Select a category")
    private Long categoryId;

    @NotBlank(message = "Subject is required")
    @Size(max = 150, message = "Subject must be 150 characters or fewer")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be under 2000 characters")
    private String description;

    private Priority priority = Priority.MEDIUM;

    private TicketStatus status = TicketStatus.OPEN;

    /** Empty / null means "unassigned". */
    private Long assignedStaffId;
}
