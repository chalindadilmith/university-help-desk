package com.helpdesk.exception;

/**
 * Thrown for a validated-but-still-rejected business action, e.g. a routing
 * rule that conflicts with an existing one (UC-F3-01 extension 4a), or a
 * course registration that exceeds the credit limit (UC-08 extension 6b).
 * Distinct from DuplicateResourceException (unique-key collisions) so
 * controllers can show the right message either way.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
