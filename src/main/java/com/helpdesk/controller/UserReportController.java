package com.helpdesk.controller;

import com.helpdesk.entity.AccountStatus;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.User;
import com.helpdesk.service.OnlineUserService;
import com.helpdesk.service.UserReportService;
import com.helpdesk.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Staff and Admin: who is logged in, every user's details, and a full report for each member.
 * SecurityConfig limits /reports/** to those two roles. Password hashes are never exposed here.
 */
@Controller
@RequestMapping("/reports/users")
public class UserReportController {

    private final UserService userService;
    private final OnlineUserService onlineUserService;
    private final UserReportService reportService;

    public UserReportController(UserService userService, OnlineUserService onlineUserService,
                                UserReportService reportService) {
        this.userService = userService;
        this.onlineUserService = onlineUserService;
        this.reportService = reportService;
    }

    @GetMapping
    public String directory(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Role role,
                            @RequestParam(required = false) String view,
                            Model model) {
        List<OnlineUserService.OnlineEntry> online = onlineUserService.online();
        Set<Long> onlineIds = onlineUserService.onlineUserIds();
        List<User> everyone = userService.searchUsers(null, null);
        List<User> users = userService.searchUsers(keyword, role).stream()
                .filter(u -> !"online".equals(view) || onlineIds.contains(u.getId()))
                .toList();

        model.addAttribute("online", online);
        model.addAttribute("onlineIds", onlineIds);
        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedRole", role);
        model.addAttribute("view", view);
        model.addAttribute("totalUsers", everyone.size());
        model.addAttribute("studentCount", everyone.stream().filter(u -> u.getRole() == Role.STUDENT).count());
        model.addAttribute("staffCount", everyone.stream().filter(u -> u.getRole() == Role.STAFF).count());
        model.addAttribute("adminCount", everyone.stream().filter(u -> u.getRole() == Role.ADMIN).count());
        model.addAttribute("deactivatedCount", everyone.stream().filter(u -> u.getStatus() == AccountStatus.DEACTIVATED).count());
        model.addAttribute("activeWindow", OnlineUserService.ACTIVE_WINDOW_MINUTES);
        return "reports/users";
    }

    @GetMapping("/{id}")
    public String report(@PathVariable Long id, Model model) {
        model.addAttribute("r", reportService.build(id));
        model.addAttribute("online", onlineUserService.onlineUserIds().contains(id));
        return "reports/user-report";
    }

    @GetMapping("/{id}/export.csv")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        String csv = reportService.toCsv(id);
        User user = userService.getById(id);
        String safeName = user.getFullName().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"member-report-" + safeName + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
