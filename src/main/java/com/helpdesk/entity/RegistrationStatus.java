package com.helpdesk.entity;

/** No payment step in this MVP (Phase 0 decision) - CONFIRMED is the final successful state. */
public enum RegistrationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
