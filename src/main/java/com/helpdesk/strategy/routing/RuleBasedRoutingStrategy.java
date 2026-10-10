package com.helpdesk.strategy.routing;

import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.RoutingRule;
import com.helpdesk.entity.TicketCategory;
import com.helpdesk.repository.RoutingRuleRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** ConcreteStrategy 1: the admin-defined rule for (category + priority) - UC-F3-01 step 4. */
@Component
@Order(1)
public class RuleBasedRoutingStrategy implements TicketRoutingStrategy {

    private final RoutingRuleRepository routingRuleRepository;

    public RuleBasedRoutingStrategy(RoutingRuleRepository routingRuleRepository) {
        this.routingRuleRepository = routingRuleRepository;
    }

    @Override
    public Optional<JobPool> route(TicketCategory category, Priority priority) {
        return routingRuleRepository.findByCategoryAndPriority(category, priority).map(RoutingRule::getTargetPool);
    }
}
