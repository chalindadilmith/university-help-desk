package com.helpdesk.controller;

import com.helpdesk.dto.ReportItemForm;
import com.helpdesk.entity.ItemType;
import com.helpdesk.entity.LostFoundClaim;
import com.helpdesk.entity.LostFoundItem;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.LostFoundService;
import com.helpdesk.service.ProofStorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC-F1-01: students report items, search, and claim (with photo evidence). */
@Controller
@RequestMapping("/student/lost-found")
public class LostFoundController {

    private final LostFoundService lostFoundService;
    private final ProofStorageService proofStorageService;

    public LostFoundController(LostFoundService lostFoundService, ProofStorageService proofStorageService) {
        this.lostFoundService = lostFoundService;
        this.proofStorageService = proofStorageService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) ItemType type,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        model.addAttribute("items", lostFoundService.search(type, keyword));
        model.addAttribute("selectedType", type);
        model.addAttribute("keyword", keyword);
        if (!model.containsAttribute("reportForm")) {
            model.addAttribute("reportForm", new ReportItemForm());
        }
        return "student/lost-found";
    }

    @PostMapping("/report")
    public String report(@Valid @ModelAttribute("reportForm") ReportItemForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails principal,
                         Model model) {
        if (form.getImage() == null || form.getImage().isEmpty()) {
            bindingResult.rejectValue("image", "required", "Please upload a photo of the item (PNG or JPG).");
        }
        if (!bindingResult.hasErrors()) {
            try {
                lostFoundService.reportItem(principal.getUser(), form);
                return "redirect:/student/lost-found?reported=1";
            } catch (BusinessRuleException e) {
                bindingResult.rejectValue("image", "invalid", e.getMessage());
            }
        }
        model.addAttribute("items", lostFoundService.search(null, null));
        return "student/lost-found";
    }

    /** "My reports" and "My claims" for the logged-in student. */
    @GetMapping("/mine")
    public String mine(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        Long userId = principal.getUser().getId();
        model.addAttribute("reports", lostFoundService.myReports(userId));
        model.addAttribute("claims", lostFoundService.myClaims(userId));
        return "student/lost-found-mine";
    }

    /** Edit form for the reporter's own ACTIVE report, pre-filled from the stored item. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal CustomUserDetails principal,
                           Model model) {
        LostFoundItem item = lostFoundService.getById(id);
        checkEditable(item, principal);
        if (!model.containsAttribute("reportForm")) {
            ReportItemForm form = new ReportItemForm();
            form.setItemType(item.getItemType());
            form.setDescription(item.getDescription());
            form.setLocation(item.getLocation());
            form.setItemDate(item.getItemDate());
            model.addAttribute("reportForm", form);
        }
        model.addAttribute("item", item);
        return "student/lost-found-edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute("reportForm") ReportItemForm form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal CustomUserDetails principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        LostFoundItem item = lostFoundService.getById(id);
        checkEditable(item, principal);
        if (!bindingResult.hasErrors()) {
            try {
                lostFoundService.editReport(principal.getUser(), id, form);
                redirectAttributes.addFlashAttribute("success", "Your report was updated.");
                return "redirect:/student/lost-found/" + id;
            } catch (BusinessRuleException e) {
                bindingResult.rejectValue("image", "invalid", e.getMessage());
            }
        }
        model.addAttribute("item", item);
        return "student/lost-found-edit";
    }

    private void checkEditable(LostFoundItem item, CustomUserDetails principal) {
        if (!item.getReporter().getId().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("You can only edit reports you made.");
        }
        if (item.getStatus() != com.helpdesk.entity.ItemStatus.ACTIVE) {
            throw new BusinessRuleException("Only active reports can be edited.");
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        LostFoundItem item = lostFoundService.getById(id);
        Long userId = principal.getUser().getId();
        model.addAttribute("item", item);
        model.addAttribute("ownReport", item.getReporter().getId().equals(userId));
        model.addAttribute("myClaim", lostFoundService.findClaim(id, userId).orElse(null));
        return "student/lost-found-detail";
    }

    /** Submit a claim, or resubmit after a rejection - both need a photo. */
    @PostMapping("/{id}/claim")
    public String claim(@PathVariable Long id,
                        @RequestParam(required = false) String proofDescription,
                        @RequestParam(value = "proofFile", required = false) MultipartFile proofFile,
                        @AuthenticationPrincipal CustomUserDetails principal,
                        RedirectAttributes redirectAttributes) {
        try {
            lostFoundService.claimItem(principal.getUser(), id, proofDescription, proofFile);
            redirectAttributes.addFlashAttribute("success",
                    "Claim submitted. An admin will compare your photo and description with the reporter's and verify it. You'll get a notification with the result.");
        } catch (IllegalStateException | BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/lost-found/" + id;
    }

    /** The reporter deletes their own report, e.g. because they found the item themselves. */
    @PostMapping("/{id}/delete")
    public String deleteReport(@PathVariable Long id,
                               @AuthenticationPrincipal CustomUserDetails principal,
                               RedirectAttributes redirectAttributes) {
        lostFoundService.deleteReport(principal.getUser(), id);
        redirectAttributes.addFlashAttribute("success",
                "Your report was deleted. Anyone who had claimed it has been notified.");
        return "redirect:/student/lost-found";
    }

    /** The claimant deletes their own claim, e.g. because they met the owner directly. */
    @PostMapping("/claims/{claimId}/delete")
    public String deleteClaim(@PathVariable Long claimId,
                              @AuthenticationPrincipal CustomUserDetails principal,
                              RedirectAttributes redirectAttributes) {
        LostFoundClaim claim = lostFoundService.getClaim(claimId);
        Long itemId = claim.getItem().getId();
        lostFoundService.deleteClaim(principal.getUser(), claimId);
        redirectAttributes.addFlashAttribute("success", "Your claim was deleted. The reporter has been notified.");
        return "redirect:/student/lost-found/" + itemId;
    }

    /** The reporter can see their own item photo; other students never can (it is the proof used to verify claims). */
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> itemImage(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        LostFoundItem item = lostFoundService.getById(id);
        if (!item.getReporter().getId().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("You can only view photos of items you reported.");
        }
        return stream(item.getImageFilePath(), "item-" + id);
    }

    /** A claimant can see their own evidence photo. */
    @GetMapping("/claims/{claimId}/proof")
    public ResponseEntity<Resource> myProof(@PathVariable Long claimId, @AuthenticationPrincipal CustomUserDetails principal) {
        LostFoundClaim claim = lostFoundService.getClaim(claimId);
        if (!claim.getClaimant().getId().equals(principal.getUser().getId())) {
            throw new AccessDeniedException("This claim does not belong to you.");
        }
        return stream(claim.getProofFilePath(), "claim-" + claimId);
    }

    private ResponseEntity<Resource> stream(String storedName, String label) {
        if (storedName == null) {
            throw new com.helpdesk.exception.ResourceNotFoundException("No photo was uploaded.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proofStorageService.contentTypeOf(storedName)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + label + "-" + storedName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(proofStorageService.load(storedName));
    }
}
