package com.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form-backing object for student self-registration (UC-01). Kept separate
 * from the User entity so a Thymeleaf form is never bound directly to a JPA
 * entity (that would let a malicious form field overwrite fields like
 * "role" or "status" - a classic mass-assignment mistake).
 *
 * Password complexity here (min 8 chars) is an OPTIONAL IMPROVEMENT baseline
 * since the source documents left the exact rule undefined (Phase 0, Open
 * Issue). Tighten it later if your team defines a stricter policy.
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must be under 150 characters")
    private String fullName;

    @NotBlank(message = "University ID is required")
    @Size(max = 30, message = "University ID must be under 30 characters")
    private String universityId;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
