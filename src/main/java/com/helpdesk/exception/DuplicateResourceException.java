package com.helpdesk.exception;

import lombok.Getter;

/**
 * Thrown when a registration/creation request collides with an existing
 * unique value (e.g. email or university ID already in use). Carries the
 * form field name so the controller can attach the error to the right
 * input instead of showing a generic page-level message.
 */
@Getter
public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String field, String message) {
        super(message);
        this.field = field;
    }
}
