package com.helpdesk.service.impl;

import com.helpdesk.dto.RegisterForm;
import com.helpdesk.dto.StaffCreateForm;
import com.helpdesk.dto.StaffCreationResult;
import com.helpdesk.entity.AccountStatus;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.User;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.UserService;
import com.helpdesk.util.IdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final com.helpdesk.repository.JobPoolRepository jobPoolRepository;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            AuditLogService auditLogService,
                            com.helpdesk.repository.JobPoolRepository jobPoolRepository) {
        this.jobPoolRepository = jobPoolRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public User registerStudent(RegisterForm form) {
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new DuplicateResourceException("email", "This email is already registered.");
        }
        if (userRepository.existsByUniversityId(form.getUniversityId())) {
            throw new DuplicateResourceException("universityId", "This university ID is already registered.");
        }

        User user = User.builder()
                .fullName(form.getFullName())
                .universityId(form.getUniversityId())
                .email(form.getEmail())
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .role(Role.STUDENT)
                .status(AccountStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        // Unique system student ID, derived from the generated primary key (e.g. STU202600012).
        saved.setStudentCode(IdGenerator.studentCode(saved.getId(), saved.getCreatedAt()));
        return saved;
    }

    @Override
    @Transactional
    public StaffCreationResult createStaffAccount(StaffCreateForm form, User actingAdmin) {
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new DuplicateResourceException("email", "This email is already registered.");
        }
        if (userRepository.existsByUniversityId(form.getUniversityId())) {
            throw new DuplicateResourceException("universityId", "This university/staff ID is already registered.");
        }

        String tempPassword = generateTempPassword();

        User user = User.builder()
                .fullName(form.getFullName())
                .universityId(form.getUniversityId())
                .email(form.getEmail())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(form.getRole() == null ? Role.STAFF : form.getRole())
                .status(AccountStatus.ACTIVE)
                .mustChangePassword(true) // temporary password -> must be replaced on first login
                .build();

        User saved = userRepository.save(user);

        auditLogService.log(actingAdmin, "CREATE_STAFF_ACCOUNT", "User", saved.getId(),
                "Created " + saved.getRole() + " account for " + saved.getEmail());

        return new StaffCreationResult(saved, tempPassword);
    }

    @Override
    public List<User> findAllStaffAndAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.STAFF || u.getRole() == Role.ADMIN)
                .toList();
    }

    @Override
    public List<User> searchUsers(String keyword, Role role) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> q.isEmpty()
                        || contains(u.getFullName(), q)
                        || contains(u.getEmail(), q)
                        || contains(u.getUniversityId(), q)
                        || contains(u.getStudentCode(), q))
                .sorted(java.util.Comparator.comparing(User::getRole).thenComparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean contains(String value, String lowerQuery) {
        return value != null && value.toLowerCase().contains(lowerQuery);
    }

    @Override
    @Transactional
    public User deactivateUser(Long userId, User actingAdmin) {
        if (actingAdmin != null && userId.equals(actingAdmin.getId())) {
            throw new BusinessRuleException("You cannot deactivate your own account.");
        }
        User user = getById(userId);
        if (user.getRole() == Role.ADMIN && user.getStatus() == AccountStatus.ACTIVE
                && userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new BusinessRuleException("You cannot deactivate the last remaining active admin.");
        }
        user.setStatus(AccountStatus.DEACTIVATED);
        auditLogService.log(actingAdmin, "DEACTIVATE_USER", "User", userId, "Deactivated " + user.getEmail());
        return user;
    }

    @Override
    @Transactional
    public User updateUser(Long userId, com.helpdesk.dto.UserEditForm form, User actingAdmin) {
        User user = getById(userId);
        String fullName = form.getFullName().trim();
        String email = form.getEmail().trim();
        String universityId = form.getUniversityId().trim();
        Role newRole = form.getRole() == null ? user.getRole() : form.getRole();

        userRepository.findByEmail(email)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> { throw new DuplicateResourceException("email", "This email is already registered."); });
        userRepository.findByUniversityId(universityId)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> { throw new DuplicateResourceException("universityId", "This university/staff ID is already registered."); });

        Role oldRole = user.getRole();
        if (oldRole != newRole) {
            if (actingAdmin != null && userId.equals(actingAdmin.getId())) {
                throw new BusinessRuleException("You cannot change your own role.");
            }
            if (oldRole == Role.ADMIN && user.getStatus() == AccountStatus.ACTIVE
                    && userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE) <= 1) {
                throw new BusinessRuleException("You cannot demote the last remaining active admin.");
            }
        }

        String before = user.getFullName() + " / " + user.getEmail() + " / " + user.getUniversityId() + " / " + oldRole;
        user.setFullName(fullName);
        user.setEmail(email);
        user.setUniversityId(universityId);
        user.setRole(newRole);

        if (oldRole != newRole) {
            if (newRole == Role.STUDENT && user.getStudentCode() == null) {
                user.setStudentCode(IdGenerator.studentCode(user.getId(), user.getCreatedAt()));
            }
            if (oldRole == Role.STAFF) {
                // no longer staff: take them out of every job pool so they stop receiving tickets
                for (com.helpdesk.entity.JobPool pool : jobPoolRepository.findByStaffMembersId(userId)) {
                    pool.getStaffMembers().removeIf(m -> m.getId().equals(userId));
                }
            }
        }
        userRepository.save(user);

        auditLogService.log(actingAdmin, "EDIT_USER", "User", userId,
                "Edited user. Before: " + before + " | After: " + fullName + " / " + email + " / " + universityId + " / " + newRole);
        return user;
    }

    @Override
    @Transactional
    public User activateUser(Long userId, User actingAdmin) {
        User user = getById(userId);
        user.setStatus(AccountStatus.ACTIVE);
        auditLogService.log(actingAdmin, "ACTIVATE_USER", "User", userId, "Reactivated " + user.getEmail());
        return user;
    }

    @Override
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Override
    @Transactional
    public User updateOwnProfile(Long userId, String fullName) {
        User user = getById(userId);
        user.setFullName(fullName);
        return user;
    }

    @Override
    @Transactional
    public User changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword) {
        User user = getById(userId);
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Your current password is incorrect.");
        }
        String policyProblem = com.helpdesk.util.PasswordPolicy.check(newPassword);
        if (policyProblem != null) {
            throw new BusinessRuleException(policyProblem);
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessRuleException("The new password and its confirmation do not match.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different from your current password.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        auditLogService.log(user, "CHANGE_PASSWORD", "User", userId, "Changed own password");
        return user;
    }

    @Override
    @Transactional
    public StaffCreationResult resetPassword(Long userId, User actingAdmin) {
        if (actingAdmin != null && userId.equals(actingAdmin.getId())) {
            throw new BusinessRuleException("Use Profile > Change Password to change your own password.");
        }
        User user = getById(userId);
        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        auditLogService.log(actingAdmin, "RESET_PASSWORD", "User", userId, "Reset password for " + user.getEmail());
        return new StaffCreationResult(user, tempPassword);
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
