package com.helpdesk.exception;

/** Thrown when a lookup by id (ticket, user, item, etc.) finds nothing. Mapped to a 404 page by GlobalExceptionHandler. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
