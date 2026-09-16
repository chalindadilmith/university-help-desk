package com.helpdesk.service;

import com.helpdesk.entity.Notification;
import com.helpdesk.entity.User;

import java.util.List;

public interface NotificationService {

    Notification notify(User recipient, String type, String message, String relatedEntityType, Long relatedEntityId);

    List<Notification> recentForUser(Long userId);

    long unreadCount(Long userId);

    void markAllRead(Long userId);
}
