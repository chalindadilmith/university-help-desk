package com.helpdesk.entity;

/**
 * Lifecycle status of a user account.
 * ACTIVE       - can log in normally.
 * PENDING      - staff account created by Admin but not yet activated (UC-02).
 * DEACTIVATED  - login blocked, kept for record/audit purposes.
 */
public enum AccountStatus {
    ACTIVE,
    PENDING,
    DEACTIVATED
}
