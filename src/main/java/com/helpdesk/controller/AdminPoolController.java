package com.helpdesk.controller;

import com.helpdesk.dto.PoolForm;
import com.helpdesk.dto.StaffCreateForm;
import com.helpdesk.dto.StaffCreationResult;
import com.helpdesk.entity.AccountStatus;
import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.TicketCategory;
import com.helpdesk.entity.User;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff work-pools. A pool is the "role" a staff member has in the help desk (for example IT Support
 * or Academic Affairs), so assigning staff to pools lives here and not on the Users &amp; Staff page.
 */
@Controller
@RequestMapping("/admin/pools")
public class AdminPoolController {

    private final AdminConfigService adminConfigService;
    private final UserService userService;

    public AdminPoolController(AdminConfigService adminConfigService, UserService userService) {
        this.adminConfigService = adminConfigService;
        this.userService = userService;
    }

    @GetMapping
    public String index(Model model) {
        List<JobPool> pools = adminConfigService.listPools();
        List<User> activeStaff = userService.findAllStaffAndAdmins().stream()
                .filter(u -> u.getRole() == Role.STAFF && u.getStatus() == AccountStatus.ACTIVE)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        // categories served by each pool
        Map<Long, List<TicketCategory>> categoriesByPool = new LinkedHashMap<>();
        for (TicketCategory c : adminConfigService.listCategories()) {
            if (c.getPool() != null) {
                categoriesByPool.computeIfAbsent(c.getPool().getId(), k -> new ArrayList<>()).add(c);
            }
        }
        // which pools (= roles) each staff member belongs to
        Map<Long, List<String>> rolesByStaff = new LinkedHashMap<>();
        // staff who could still be added to a given pool
        Map<Long, List<User>> availableByPool = new LinkedHashMap<>();
        for (JobPool pool : pools) {
            for (User m : pool.getStaffMembers()) {
                rolesByStaff.computeIfAbsent(m.getId(), k -> new ArrayList<>()).add(pool.getName());
            }
            availableByPool.put(pool.getId(), activeStaff.stream()
                    .filter(s -> pool.getStaffMembers().stream().noneMatch(m -> m.getId().equals(s.getId())))
                    .toList());
        }

        model.addAttribute("pools", pools);
        model.addAttribute("activeStaff", activeStaff);
        model.addAttribute("categoriesByPool", categoriesByPool);
        model.addAttribute("rolesByStaff", rolesByStaff);
        model.addAttribute("availableByPool", availableByPool);
        if (!model.containsAttribute("poolForm")) model.addAttribute("poolForm", new PoolForm());
        if (!model.containsAttribute("staffForm")) {
            StaffCreateForm form = new StaffCreateForm();
            form.setRole(Role.STAFF);
            model.addAttribute("staffForm", form);
        }
        return "admin/pools";
    }

    @PostMapping
    public String createPool(@Valid @ModelAttribute("poolForm") PoolForm form, BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a name for the work pool.");
            return "redirect:/admin/pools";
        }
        try {
            adminConfigService.createPool(form, principal.getUser());
            redirectAttributes.addFlashAttribute("success", "Work pool \"" + form.getName() + "\" created.");
        } catch (DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/pools";
    }

    /** "Add Staff Members": put one or more existing staff accounts into an existing pool. */
    @PostMapping("/{poolId}/staff")
    public String addStaff(@PathVariable Long poolId,
                           @RequestParam(name = "staffUserIds", required = false) List<Long> staffUserIds,
                           @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        try {
            int added = adminConfigService.addStaffToPool(poolId, staffUserIds, principal.getUser());
            redirectAttributes.addFlashAttribute("success",
                    added == 0 ? "Those staff members were already in the pool."
                            : added + (added == 1 ? " staff member was" : " staff members were") + " added to the pool.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/pools";
    }

    @PostMapping("/{poolId}/staff/{staffUserId}/remove")
    public String removeStaff(@PathVariable Long poolId, @PathVariable Long staffUserId,
                              @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        adminConfigService.removeStaffFromPool(poolId, staffUserId, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "Staff member removed from the pool.");
        return "redirect:/admin/pools";
    }

    /** Creates a brand-new staff account and puts it straight into an existing pool. */
    @PostMapping("/new-staff")
    public String newStaff(@Valid @ModelAttribute("staffForm") StaffCreateForm form, BindingResult bindingResult,
                           @RequestParam(required = false) Long poolId,
                           @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill in the name, staff ID and a valid e-mail.");
            return "redirect:/admin/pools";
        }
        try {
            form.setRole(Role.STAFF);
            StaffCreationResult result = userService.createStaffAccount(form, principal.getUser());
            if (poolId != null) {
                adminConfigService.addStaffToPool(poolId, List.of(result.user().getId()), principal.getUser());
            }
            redirectAttributes.addFlashAttribute("success",
                    "Staff account created for " + result.user().getEmail()
                            + ". Temporary password (share it securely, it will not be shown again): "
                            + result.temporaryPassword() + ". They must change it at first login.");
        } catch (DuplicateResourceException | BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/pools";
    }
}
