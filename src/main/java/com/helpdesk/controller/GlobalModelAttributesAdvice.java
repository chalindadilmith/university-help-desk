package com.helpdesk.controller;

import com.helpdesk.entity.Role;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Runs before every controller method in the app and injects the
 * notification bell's data into the Model. This means the shared
 * fragments/layout.html can always read "unreadNotifCount" and
 * "recentNotifications" without every single controller (written by six
 * different people) having to remember to add them manually.
 */
@ControllerAdvice
public class GlobalModelAttributesAdvice {

    private final NotificationService notificationService;
    private final TicketRepository ticketRepository;

    public GlobalModelAttributesAdvice(NotificationService notificationService, TicketRepository ticketRepository) {
        this.notificationService = notificationService;
        this.ticketRepository = ticketRepository;
    }

    @ModelAttribute
    public void addNotificationData(Model model, @AuthenticationPrincipal CustomUserDetails principal) {
        if (principal != null) {
            Long userId = principal.getUser().getId();
            model.addAttribute("unreadNotifCount", notificationService.unreadCount(userId));
            model.addAttribute("recentNotifications", notificationService.recentForUser(userId));
            Role role = principal.getUser().getRole();
            if (role == Role.STAFF || role == Role.ADMIN) {
                // amount of tickets, shown as a badge next to the ticket links in the sidebar
                model.addAttribute("ticketTotal", ticketRepository.count());
            }
        }
    }
}
