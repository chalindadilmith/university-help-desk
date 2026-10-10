package com.helpdesk.strategy.routing;

import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.TicketCategory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** ConcreteStrategy 2 (fallback): the default pool the admin attached to the category itself. */
@Component
@Order(2)
public class CategoryPoolRoutingStrategy implements TicketRoutingStrategy {

    @Override
    public Optional<JobPool> route(TicketCategory category, Priority priority) {
        return Optional.ofNullable(category.getPool());
    }
}
