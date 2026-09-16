package com.helpdesk.entity;

/**
 * The three roles confirmed by the project documentation.
 * Stored as a STRING column (see User#role) so the database stays
 * human-readable, and mapped to Spring Security authorities as
 * "ROLE_STUDENT", "ROLE_STAFF", "ROLE_ADMIN".
 */
public enum Role {
    STUDENT,
    STAFF,
    ADMIN
}
