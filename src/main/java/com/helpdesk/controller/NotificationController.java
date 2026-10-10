package com.helpdesk.controller;

import com.helpdesk.entity.Notification;
import com.helpdesk.entity.Role;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Full notification list, "mark all as read", and click-through from a notification to what it is about. */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private static final int PAGE_SIZE = 20;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        Page<Notification> result = notificationService.page(principal.getUser().getId(), page - 1, PAGE_SIZE);
        model.addAttribute("notifications", result.getContent());
        model.addAttribute("page", result.getNumber() + 1);
        model.addAttribute("totalPages", Math.max(1, result.getTotalPages()));
        return "notifications";
    }

    @PostMapping("/read-all")
    public String readAll(@AuthenticationPrincipal CustomUserDetails principal) {
        notificationService.markAllRead(principal.getUser().getId());
        return "redirect:/notifications";
    }

    @GetMapping("/{id}/open")
    public String open(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        Notification n = notificationService.markRead(principal.getUser().getId(), id);
        if (n == null) {
            return "redirect:/notifications";
        }
        Role role = principal.getUser().getRole();
        if ("Ticket".equals(n.getRelatedEntityType()) && n.getRelatedEntityId() != null) {
            if (role == Role.STUDENT) return "redirect:/student/tickets/" + n.getRelatedEntityId();
            if (role == Role.STAFF) return "redirect:/staff/tickets/" + n.getRelatedEntityId();
            if (role == Role.ADMIN) return "redirect:/admin/tickets/" + n.getRelatedEntityId();
        }
        if ("LostFoundItem".equals(n.getRelatedEntityType()) && n.getRelatedEntityId() != null && role == Role.STUDENT) {
            return "redirect:/student/lost-found/" + n.getRelatedEntityId();
        }
        if (n.getType() != null && n.getType().startsWith("REGISTRATION")) {
            if (role == Role.STUDENT) return "redirect:/student/registration";
            if (role == Role.ADMIN) return "redirect:/admin/registrations";
        }
        return "redirect:/notifications";
    }
}
