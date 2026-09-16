package com.helpdesk.service;

import com.helpdesk.entity.AuditLog;
import com.helpdesk.entity.User;
import com.helpdesk.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Small shared helper used by any service that needs to record an
 * administrative or significant action (UC-02 postcondition: "the
 * administrative action is recorded in the activity log for audit
 * purposes"). Kept as a plain concrete @Service (no interface) since it
 * has exactly one implementation and is unlikely to ever need mocking
 * beyond a simple stub in tests.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(User actor, String action, String targetEntity, Long targetId, String details) {
        AuditLog entry = AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetEntity(targetEntity)
                .targetId(targetId)
                .details(details)
                .build();
        auditLogRepository.save(entry);
    }

    public List<AuditLog> recentLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }
}
