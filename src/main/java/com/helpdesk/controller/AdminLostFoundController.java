package com.helpdesk.controller;

import com.helpdesk.entity.LostFoundClaim;
import com.helpdesk.entity.LostFoundItem;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.LostFoundService;
import com.helpdesk.service.ProofStorageService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/** Admin compares the reporter's photo with the claimant's evidence, then verifies or rejects (UC-F1-01 step 9). */
@Controller
@RequestMapping("/admin/lost-found")
public class AdminLostFoundController {

    private final LostFoundService lostFoundService;
    private final ProofStorageService proofStorageService;

    public AdminLostFoundController(LostFoundService lostFoundService, ProofStorageService proofStorageService) {
        this.lostFoundService = lostFoundService;
        this.proofStorageService = proofStorageService;
    }

    @GetMapping
    public String pendingClaims(Model model) {
        model.addAttribute("claims", lostFoundService.pendingClaims());
        model.addAttribute("tomorrow", LocalDate.now().plusDays(1).toString());
        model.addAttribute("today", LocalDate.now().toString());
        return "admin/lost-found-claims";
    }

    /** Every report (any status) so admins can moderate duplicates or inappropriate posts. */
    @GetMapping("/items")
    public String allItems(Model model) {
        model.addAttribute("items", lostFoundService.allItems());
        return "admin/lost-found-items";
    }

    @PostMapping("/items/{itemId}/remove")
    public String removeItem(@PathVariable Long itemId, @RequestParam(required = false) String reason,
                             @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        try {
            lostFoundService.adminRemoveItem(principal.getUser(), itemId, reason);
            redirectAttributes.addFlashAttribute("success", "The report was removed and the people involved were notified.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lost-found/items";
    }

    @PostMapping("/{claimId}/approve")
    public String approve(@PathVariable Long claimId,
                          @RequestParam(required = false) String collectionLocation,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate collectionDate,
                          @RequestParam(required = false) String collectionTime,
                          @RequestParam(required = false) String collectionNote,
                          @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        try {
            lostFoundService.approveClaim(claimId, principal.getUser(), collectionLocation, collectionDate,
                    collectionTime, collectionNote);
            redirectAttributes.addFlashAttribute("success",
                    "Claim verified. The claimant was notified (and emailed) with the collection place, date and reference number. Other pending claims on this item were rejected automatically.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lost-found";
    }

    @PostMapping("/{claimId}/reject")
    public String reject(@PathVariable Long claimId, @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        try {
            lostFoundService.rejectClaim(claimId, principal.getUser(), reason);
            redirectAttributes.addFlashAttribute("success",
                    "Claim rejected. The claimant was notified with your reason and can resubmit with stronger evidence.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lost-found";
    }

    /** Claimant's evidence photo. Admin-only via SecurityConfig (/admin/**). */
    @GetMapping("/claims/{claimId}/proof")
    public ResponseEntity<Resource> proof(@PathVariable Long claimId) {
        LostFoundClaim claim = lostFoundService.getClaim(claimId);
        return stream(claim.getProofFilePath(), "claim-" + claimId);
    }

    /** Reporter's item photo. Admin-only. */
    @GetMapping("/items/{itemId}/image")
    public ResponseEntity<Resource> itemImage(@PathVariable Long itemId) {
        LostFoundItem item = lostFoundService.getById(itemId);
        return stream(item.getImageFilePath(), "item-" + itemId);
    }

    private ResponseEntity<Resource> stream(String storedName, String label) {
        if (storedName == null) {
            throw new ResourceNotFoundException("No photo was uploaded.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proofStorageService.contentTypeOf(storedName)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + label + "-" + storedName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(proofStorageService.load(storedName));
    }
}
