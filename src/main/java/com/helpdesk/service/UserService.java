package com.helpdesk.service;

import com.helpdesk.dto.RegisterForm;
import com.helpdesk.dto.StaffCreateForm;
import com.helpdesk.dto.StaffCreationResult;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.User;

import java.util.List;

public interface UserService {

    /**
     * Creates a new STUDENT account (UC-01 self-registration path).
     *
     * @throws com.helpdesk.exception.DuplicateResourceException if the email or university ID is already taken
     */
    User registerStudent(RegisterForm form);

    /**
     * Admin-only: creates a Staff or Admin account with a generated temporary
     * password (UC-02). The plaintext password is returned exactly once.
     */
    StaffCreationResult createStaffAccount(StaffCreateForm form, User actingAdmin);

    List<User> findAllStaffAndAdmins();

    /**
     * Admin user management: every account (students included), optionally filtered by
     * a role and a case-insensitive keyword matched against name, email, university ID
     * and student ID.
     */
    List<User> searchUsers(String keyword, Role role);

    /**
     * Admin-only: edits name, email, university ID and role. Duplicate email / university ID are
     * rejected (excluding the account itself); an admin cannot change their own role or demote the
     * last active admin. The change is written to the audit log.
     *
     * @throws com.helpdesk.exception.DuplicateResourceException on a clash
     * @throws com.helpdesk.exception.BusinessRuleException on a blocked role change
     */
    User updateUser(Long userId, com.helpdesk.dto.UserEditForm form, User actingAdmin);

    User deactivateUser(Long userId, User actingAdmin);

    User activateUser(Long userId, User actingAdmin);

    User getById(Long id);

    User updateOwnProfile(Long userId, String fullName);

    /**
     * Self-service password change. Verifies the old password, requires the new one to
     * be confirmed, meet the password policy (8-100 characters with a letter and a number) and be different from the old one, and clears the
     * forced-change flag.
     *
     * @throws com.helpdesk.exception.BusinessRuleException on any validation failure
     */
    User changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword);

    /**
     * Admin-only: sets a new generated temporary password for any user (student, staff
     * or admin) and forces them to change it at next login. Returns the plaintext
     * password exactly once.
     */
    StaffCreationResult resetPassword(Long userId, User actingAdmin);
}
