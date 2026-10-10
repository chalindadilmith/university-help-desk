package com.helpdesk.repository;

import com.helpdesk.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop20ByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);
    List<Notification> findByRelatedEntityTypeAndRelatedEntityId(String relatedEntityType, Long relatedEntityId);

    org.springframework.data.domain.Page<Notification> findByRecipientIdOrderByCreatedAtDesc(
            Long recipientId, org.springframework.data.domain.Pageable pageable);

    java.util.Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    java.util.List<Notification> findByRecipientIdAndReadFalse(Long recipientId);
}
