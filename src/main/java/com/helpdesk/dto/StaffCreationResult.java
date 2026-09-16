package com.helpdesk.dto;

import com.helpdesk.entity.User;

/**
 * Returned once, right after an Admin creates a Staff/Admin account, so the
 * temporary password can be shown to the Admin on screen. This project has
 * no SMTP/email provider configured (UC-02's "system sends login
 * credentials by email" step is out of scope without one - flagged in
 * Phase 0 as an undocumented integration), so the pragmatic substitute is:
 * generate a temp password, hash it for storage, and display the plaintext
 * to the Admin exactly once so they can share it with the new staff member.
 * The plaintext is never persisted or logged anywhere.
 */
public record StaffCreationResult(User user, String temporaryPassword) {
}
