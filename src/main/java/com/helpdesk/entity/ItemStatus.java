package com.helpdesk.entity;

/**
 * ACTIVE   - visible in search results.
 * CLAIMED  - a claim has been verified, item handed over.
 * ARCHIVED - retention period elapsed (see Open Issue: archive duration undefined).
 */
public enum ItemStatus {
    ACTIVE,
    CLAIMED,
    ARCHIVED
}
