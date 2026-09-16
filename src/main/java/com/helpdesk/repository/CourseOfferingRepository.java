package com.helpdesk.repository;

import com.helpdesk.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
    List<CourseOffering> findBySemesterIdOrderByCourseCodeAsc(Long semesterId);
}
