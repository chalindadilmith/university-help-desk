package com.helpdesk.service.impl;

import com.helpdesk.dto.RegisterForm;
import com.helpdesk.dto.StaffCreateForm;
import com.helpdesk.dto.StaffCreationResult;
import com.helpdesk.entity.AccountStatus;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.User;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.UserService;
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

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            AuditLogService auditLogService) {
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

        return userRepository.save(user);
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
                .role(form.getRole())
                .status(AccountStatus.ACTIVE)
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
    @Transactional
    public User deactivateUser(Long userId, User actingAdmin) {
        User user = getById(userId);
        user.setStatus(AccountStatus.DEACTIVATED);
        auditLogService.log(actingAdmin, "DEACTIVATE_USER", "User", userId, "Deactivated " + user.getEmail());
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

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
