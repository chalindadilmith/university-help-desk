package com.helpdesk.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Table: users
 * Every account in the system - Student, Help Desk Staff, or System Admin.
 * Students self-register (UC-01). Staff/Admin accounts are created only by
 * a System Admin (UC-02) - there is no public staff signup endpoint.
 *
 * password is ALWAYS a BCrypt hash (see security/PasswordEncoderConfig in
 * Phase 4). It is never returned in a DTO or rendered in a Thymeleaf template.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_role", columnList = "role")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @NotBlank
    @Size(max = 30)
    @Column(name = "university_id", nullable = false, unique = true, length = 30)
    private String universityId;

    /**
     * System-generated unique student ID (e.g. STU202600012), assigned
     * automatically when a student account is created. Null for Staff/Admin.
     * Uniqueness is enforced by a filtered unique index (ux_users_student_code,
     * see RegistrationSchemaInitializer) because SQL Server's plain UNIQUE
     * constraint would allow only one NULL.
     */
    @Size(max = 20)
    @Column(name = "student_code", length = 20)
    private String studentCode;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** BCrypt hash - never plain text. */
    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    /**
     * True while the account still uses a temporary password (staff created by an
     * admin, or any account whose password an admin reset). The user is forced to
     * change it before using anything else (see ForcePasswordChangeInterceptor).
     */
    @Column(name = "must_change_password", nullable = false, columnDefinition = "bit default 0")
    @Builder.Default
    private boolean mustChangePassword = false;

    /** When the user last signed in (null until the first login after this field was added). */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
