package com.helpdesk.repository;

import com.helpdesk.entity.Priority;
import com.helpdesk.entity.RoutingRule;
import com.helpdesk.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {
    List<RoutingRule> findAllByOrderByCategoryNameAsc();
    Optional<RoutingRule> findByCategoryAndPriority(TicketCategory category, Priority priority);
    boolean existsByCategoryAndPriority(TicketCategory category, Priority priority);
}
