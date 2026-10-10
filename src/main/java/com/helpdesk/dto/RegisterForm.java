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
 * Password rule: 8-100 characters with at least one letter and one number
 * (see com.helpdesk.util.PasswordPolicy, shared with change/reset password).
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
    @jakarta.validation.constraints.Pattern(regexp = com.helpdesk.util.PasswordPolicy.REGEX,
            message = com.helpdesk.util.PasswordPolicy.MESSAGE)
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
