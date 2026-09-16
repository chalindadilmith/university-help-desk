package com.helpdesk.controller;

import com.helpdesk.dto.CategoryForm;
import com.helpdesk.dto.PoolForm;
import com.helpdesk.dto.RoutingRuleForm;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.Role;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.AdminConfigService;
import com.helpdesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC-F3-01: ticket categories, staff work-pools, and routing rules. Ticket routing only - see Phase 0 scope decision. */
@Controller
@RequestMapping("/admin/config")
public class AdminConfigController {

    private final AdminConfigService adminConfigService;
    private final UserService userService;

    public AdminConfigController(AdminConfigService adminConfigService, UserService userService) {
        this.adminConfigService = adminConfigService;
        this.userService = userService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("categories", adminConfigService.listCategories());
        model.addAttribute("pools", adminConfigService.listPools());
        model.addAttribute("rules", adminConfigService.listRoutingRules());
        model.addAttribute("staff", userService.findAllStaffAndAdmins().stream().filter(u -> u.getRole() == Role.STAFF).toList());
        model.addAttribute("priorities", Priority.values());
        if (!model.containsAttribute("categoryForm")) model.addAttribute("categoryForm", new CategoryForm());
        if (!model.containsAttribute("poolForm")) model.addAttribute("poolForm", new PoolForm());
        if (!model.containsAttribute("ruleForm")) model.addAttribute("ruleForm", new RoutingRuleForm());
        return "admin/config";
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult bindingResult,
                                  @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fix the category form and try again.");
            return "redirect:/admin/config";
        }
        try {
            adminConfigService.createCategory(form, principal.getUser());
        } catch (DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/config";
    }

    @PostMapping("/pools")
    public String createPool(@Valid @ModelAttribute("poolForm") PoolForm form, BindingResult bindingResult,
                              @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fix the pool form and try again.");
            return "redirect:/admin/config";
        }
        try {
            adminConfigService.createPool(form, principal.getUser());
        } catch (DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/config";
    }

    @PostMapping("/pools/{poolId}/staff")
    public String assignStaff(@PathVariable Long poolId, @RequestParam Long staffUserId,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        adminConfigService.assignStaffToPool(poolId, staffUserId, principal.getUser());
        return "redirect:/admin/config";
    }

    @PostMapping("/pools/{poolId}/staff/{staffUserId}/remove")
    public String removeStaff(@PathVariable Long poolId, @PathVariable Long staffUserId,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        adminConfigService.removeStaffFromPool(poolId, staffUserId, principal.getUser());
        return "redirect:/admin/config";
    }

    @PostMapping("/routing-rules")
    public String createRule(@Valid @ModelAttribute("ruleForm") RoutingRuleForm form, BindingResult bindingResult,
                              @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please select a category, priority, and pool.");
            return "redirect:/admin/config";
        }
        try {
            adminConfigService.createRoutingRule(form, principal.getUser());
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/config";
    }

    @PostMapping("/routing-rules/{id}/delete")
    public String deleteRule(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        adminConfigService.deleteRoutingRule(id, principal.getUser());
        return "redirect:/admin/config";
    }
}
