package com.helpdesk.strategy.routing;

import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.TicketCategory;

import java.util.Optional;

/**
 * Strategy pattern (F3 Admin Configuration / ticket routing) - Strategy role.
 * One way of deciding which staff work-pool should receive a new ticket.
 * Returns empty when this strategy has no answer, so the next strategy can try.
 */
public interface TicketRoutingStrategy {

    Optional<JobPool> route(TicketCategory category, Priority priority);
}
