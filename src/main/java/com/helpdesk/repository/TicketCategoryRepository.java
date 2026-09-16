package com.helpdesk.repository;

import com.helpdesk.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {
    List<TicketCategory> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
