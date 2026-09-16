package com.helpdesk.controller;

import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.LostFoundService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Admin verifies Lost & Found ownership claims before handover (UC-F1-01 step 9). */
@Controller
@RequestMapping("/admin/lost-found")
public class AdminLostFoundController {

    private final LostFoundService lostFoundService;

    public AdminLostFoundController(LostFoundService lostFoundService) {
        this.lostFoundService = lostFoundService;
    }

    @GetMapping
    public String pendingClaims(Model model) {
        model.addAttribute("claims", lostFoundService.pendingClaims());
        return "admin/lost-found-claims";
    }

    @PostMapping("/{claimId}/approve")
    public String approve(@PathVariable Long claimId, @AuthenticationPrincipal CustomUserDetails principal) {
        lostFoundService.decideClaim(claimId, true, principal.getUser());
        return "redirect:/admin/lost-found";
    }

    @PostMapping("/{claimId}/reject")
    public String reject(@PathVariable Long claimId, @AuthenticationPrincipal CustomUserDetails principal) {
        lostFoundService.decideClaim(claimId, false, principal.getUser());
        return "redirect:/admin/lost-found";
    }
}
