package com.helpdesk.repository;

import com.helpdesk.entity.KbCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbCategoryRepository extends JpaRepository<KbCategory, Long> {
    List<KbCategory> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
