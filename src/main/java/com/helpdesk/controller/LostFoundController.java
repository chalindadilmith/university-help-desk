package com.helpdesk.controller;

import com.helpdesk.dto.ReportItemForm;
import com.helpdesk.entity.ItemType;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.LostFoundService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student/lost-found")
public class LostFoundController {

    private final LostFoundService lostFoundService;

    public LostFoundController(LostFoundService lostFoundService) {
        this.lostFoundService = lostFoundService;
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
        if (bindingResult.hasErrors()) {
            model.addAttribute("items", lostFoundService.search(null, null));
            return "student/lost-found";
        }
        lostFoundService.reportItem(principal.getUser(), form);
        return "redirect:/student/lost-found?reported=1";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("item", lostFoundService.getById(id));
        return "student/lost-found-detail";
    }

    @PostMapping("/{id}/claim")
    public String claim(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        try {
            lostFoundService.claimItem(principal.getUser(), id);
            redirectAttributes.addFlashAttribute("success", "Claim submitted. The reporter and an admin will verify it.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/lost-found/" + id;
    }
}
