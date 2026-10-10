package com.helpdesk.repository;

import com.helpdesk.entity.Role;
import com.helpdesk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    long countByRole(Role role);

    boolean existsByEmail(String email);

    long countByRoleAndStatus(Role role, com.helpdesk.entity.AccountStatus status);

    Optional<User> findByUniversityId(String universityId);

    boolean existsByUniversityId(String universityId);

    /** Students created before the student-ID feature existed - backfilled at startup. */
    List<User> findByRoleAndStudentCodeIsNull(Role role);
}
