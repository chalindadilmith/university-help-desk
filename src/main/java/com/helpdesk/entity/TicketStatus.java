package com.helpdesk.entity;

/**
 * Full lifecycle from UC-F2-01 and UC-09:
 * OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED
 * with ESCALATED (senior/admin handling) and REOPENED (student unsatisfied,
 * or replies after resolution) as branches back into active handling.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    ESCALATED,
    RESOLVED,
    CLOSED,
    REOPENED
}
