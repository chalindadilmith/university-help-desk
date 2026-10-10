package com.helpdesk.dto;

import com.helpdesk.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin-only form for editing an existing account's name, email, university ID and role. */
@Getter
@Setter
@NoArgsConstructor
public class UserEditForm {

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

    /** Not editable any more; kept null on the form so the account keeps its current role. */
    private Role role;
}
