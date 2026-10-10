package com.helpdesk.controller;

import com.helpdesk.dto.CategoryForm;
import com.helpdesk.dto.RoutingRuleForm;
import com.helpdesk.entity.Priority;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.AdminConfigService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * UC-F3-01: ticket categories and routing rules. Both are pointed at EXISTING staff work-pools,
 * which are created and staffed on the separate Work Pools page ({@link AdminPoolController}).
 * Ticket routing only - see Phase 0 scope decision.
 */
@Controller
@RequestMapping("/admin/config")
public class AdminConfigController {

    private final AdminConfigService adminConfigService;

    public AdminConfigController(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("categories", adminConfigService.listCategories());
        model.addAttribute("pools", adminConfigService.listPools());
        model.addAttribute("rules", adminConfigService.listRoutingRules());
        model.addAttribute("priorities", Priority.values());
        if (!model.containsAttribute("categoryForm")) model.addAttribute("categoryForm", new CategoryForm());
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
            redirectAttributes.addFlashAttribute("success", "Category added.");
        } catch (DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/config";
    }

    /** Re-assigns an existing category to a different existing work-pool. */
    @PostMapping("/categories/{id}/pool")
    public String updateCategoryPool(@PathVariable Long id, @RequestParam(required = false) Long poolId,
                                     @AuthenticationPrincipal CustomUserDetails principal,
                                     RedirectAttributes redirectAttributes) {
        adminConfigService.updateCategoryPool(id, poolId, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "Category pool updated.");
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
            redirectAttributes.addFlashAttribute("success", "Routing rule added.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/config";
    }

    /** Sends an existing routing rule to a different existing work-pool. */
    @PostMapping("/routing-rules/{id}/pool")
    public String updateRulePool(@PathVariable Long id, @RequestParam Long poolId,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        adminConfigService.updateRoutingRulePool(id, poolId, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "Routing rule updated.");
        return "redirect:/admin/config";
    }

    @PostMapping("/routing-rules/{id}/delete")
    public String deleteRule(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                             RedirectAttributes redirectAttributes) {
        adminConfigService.deleteRoutingRule(id, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "Routing rule deleted.");
        return "redirect:/admin/config";
    }
}
