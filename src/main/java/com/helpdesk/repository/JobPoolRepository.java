package com.helpdesk.repository;

import com.helpdesk.entity.JobPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPoolRepository extends JpaRepository<JobPool, Long> {
    List<JobPool> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
