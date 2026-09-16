package com.helpdesk.service;

import com.helpdesk.dto.RegisterForm;
import com.helpdesk.dto.StaffCreateForm;
import com.helpdesk.dto.StaffCreationResult;
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

    User deactivateUser(Long userId, User actingAdmin);

    User activateUser(Long userId, User actingAdmin);

    User getById(Long id);

    User updateOwnProfile(Long userId, String fullName);
}
