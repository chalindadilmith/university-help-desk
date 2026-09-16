package com.helpdesk.dto;

import com.helpdesk.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-only account creation form (UC-02). There is no self-signup path
 * for Staff/Admin - only a System Admin can reach this form (enforced by
 * SecurityConfig restricting /admin/** to ROLE_ADMIN).
 */
@Getter
@Setter
@NoArgsConstructor
public class StaffCreateForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "University/Staff ID is required")
    @Size(max = 30)
    private String universityId;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 150)
    private String email;

    @NotNull(message = "Select a role")
    private Role role;
}
