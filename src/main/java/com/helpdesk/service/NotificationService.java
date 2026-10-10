package com.helpdesk.service;

import com.helpdesk.entity.Notification;
import com.helpdesk.entity.User;

import java.util.List;

public interface NotificationService {

    Notification notify(User recipient, String type, String message, String relatedEntityType, Long relatedEntityId);

    List<Notification> recentForUser(Long userId);

    long unreadCount(Long userId);

    void markAllRead(Long userId);

    org.springframework.data.domain.Page<Notification> page(Long userId, int page, int size);

    /** Marks one of the user's own notifications as read and returns it (null if it is not theirs). */
    Notification markRead(Long userId, Long notificationId);
}
