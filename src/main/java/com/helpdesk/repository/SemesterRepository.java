package com.helpdesk.repository;

import com.helpdesk.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    List<Semester> findAllByOrderByStartDateDesc();
    List<Semester> findByRegistrationOpenTrue();
}
