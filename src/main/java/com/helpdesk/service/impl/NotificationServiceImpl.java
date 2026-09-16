package com.helpdesk.service.impl;

import com.helpdesk.entity.Notification;
import com.helpdesk.entity.User;
import com.helpdesk.repository.NotificationRepository;
import com.helpdesk.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notifications are created directly by other services (Ticket, LostFound,
 * Feedback, Registration) when something the recipient cares about happens
 * (UC-F5-01 step 1). There's no message queue - this is a simple, synchronous
 * INSERT, which is perfectly adequate at this project's scale and keeps the
 * architecture easy to explain and demo.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public Notification notify(User recipient, String type, String message, String relatedEntityType, Long relatedEntityId) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .read(false)
                .build();
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> recentForUser(Long userId) {
        return notificationRepository.findTop20ByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        List<Notification> notifications = notificationRepository.findTop20ByRecipientIdOrderByCreatedAtDesc(userId);
        notifications.forEach(n -> n.setRead(true));
    }
}
