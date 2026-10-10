package com.helpdesk.entity;

/**
 * Lifecycle of a semester registration.
 *
 * PENDING   - submitted by the student, waiting for an Admin to review it
 * APPROVED  - approved by an Admin (final successful state; replaces the old CONFIRMED)
 * REJECTED  - rejected by an Admin (remarks explain why); the student may resubmit
 * CANCELLED - withdrawn by the student before it was reviewed
 *
 * Old rows saved as CONFIRMED are converted to APPROVED automatically at
 * startup by config/RegistrationSchemaInitializer (and by
 * database/semester-registration-migration.sql).
 */
public enum RegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
