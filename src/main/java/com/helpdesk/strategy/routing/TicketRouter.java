package com.helpdesk.strategy.routing;

import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.TicketCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strategy pattern (F3) - Context role.
 * Spring injects the strategies already sorted by their @Order, so the order of attempts is explicit.
 * The first strategy that returns a pool wins; if none does the ticket stays unrouted (null).
 */
@Component
public class TicketRouter {

    private final List<TicketRoutingStrategy> strategies;

    public TicketRouter(List<TicketRoutingStrategy> strategies) {
        this.strategies = strategies;
    }

    public JobPool route(TicketCategory category, Priority priority) {
        for (TicketRoutingStrategy strategy : strategies) {
            JobPool pool = strategy.route(category, priority).orElse(null);
            if (pool != null) {
                return pool;
            }
        }
        return null;
    }
}
