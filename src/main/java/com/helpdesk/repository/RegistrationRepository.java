package com.helpdesk.repository;

import com.helpdesk.entity.Registration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Optional<Registration> findByStudentIdAndSemesterId(Long studentId, Long semesterId);
    List<Registration> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}
