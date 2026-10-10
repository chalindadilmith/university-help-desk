package com.helpdesk.util;

import java.time.LocalDateTime;

/**
 * Builds the human-readable unique IDs used by the system. Both are derived
 * from the database primary key, so they are guaranteed unique without any
 * extra sequence table:
 *
 *   Student ID           STU + year joined + 5-digit user id  -> STU202600012
 *   Registration number  SR + year + '-' + 6-digit reg id     -> SR2026-000045
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String studentCode(Long userId, LocalDateTime createdAt) {
        int year = (createdAt != null ? createdAt : LocalDateTime.now()).getYear();
        return String.format("STU%d%05d", year, userId);
    }

    public static String registrationReference(Long registrationId, LocalDateTime createdAt) {
        int year = (createdAt != null ? createdAt : LocalDateTime.now()).getYear();
        return String.format("SR%d-%06d", year, registrationId);
    }
}
