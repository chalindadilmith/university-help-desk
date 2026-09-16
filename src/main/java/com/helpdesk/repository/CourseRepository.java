package com.helpdesk.repository;

import com.helpdesk.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findAllByOrderByCodeAsc();
    boolean existsByCodeIgnoreCase(String code);
}
