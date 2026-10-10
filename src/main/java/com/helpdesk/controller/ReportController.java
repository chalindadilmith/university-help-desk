package com.helpdesk.controller;

import com.helpdesk.service.AdminConfigService;
import com.helpdesk.service.ReportingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Shared by Staff and Admin (both need visibility into ticket/feedback
 * stats per UC-F5-01). SecurityConfig restricts /reports/** to those two roles.
 */
@Controller
public class ReportController {

    private final ReportingService reportingService;
    private final AdminConfigService adminConfigService;

    public ReportController(ReportingService reportingService, AdminConfigService adminConfigService) {
        this.reportingService = reportingService;
        this.adminConfigService = adminConfigService;
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) Long categoryId,
                          Model model) {
        model.addAttribute("report", reportingService.generate(from, to, categoryId));
        model.addAttribute("categories", adminConfigService.listCategories());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("rangeInvalid", from != null && to != null && from.isAfter(to));
        return "reports";
    }

    /** Strategy pattern: /reports/export/csv, /reports/export/json ... - the format picks the export strategy. */
    @GetMapping("/reports/export/{format}")
    public ResponseEntity<byte[]> exportAs(@org.springframework.web.bind.annotation.PathVariable String format,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) Long categoryId) {
        com.helpdesk.strategy.export.ExportedFile file = reportingService.export(format, from, to, categoryId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.body());
    }

    @GetMapping("/reports/export.csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long categoryId) {
        String csv = reportingService.exportCsv(from, to, categoryId);
        // leading byte-order mark so Excel opens it as UTF-8
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tickets-report.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
