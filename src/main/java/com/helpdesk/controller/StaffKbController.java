package com.helpdesk.controller;

import com.helpdesk.dto.ArticleForm;
import com.helpdesk.entity.KbArticle;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** UC-11: staff create/edit/remove FAQ articles, published immediately (no approval workflow in this MVP). */
@Controller
@RequestMapping("/staff/kb")
public class StaffKbController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;

    public StaffKbController(KnowledgeBaseService knowledgeBaseService, AuditLogService auditLogService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long editId,
                       @RequestParam(required = false) String q, Model model) {
        fillLists(model, q);
        if (editId != null) {
            var article = knowledgeBaseService.getById(editId);
            ArticleForm form = new ArticleForm();
            form.setId(article.getId());
            form.setTitle(article.getTitle());
            form.setContent(article.getContent());
            form.setCategoryId(article.getCategory().getId());
            form.setStatus(article.getStatus());
            model.addAttribute("articleForm", form);
        } else if (!model.containsAttribute("articleForm")) {
            model.addAttribute("articleForm", new ArticleForm());
        }
        return "staff/kb";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("articleForm") ArticleForm form, BindingResult bindingResult,
                        @AuthenticationPrincipal CustomUserDetails principal, Model model,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            fillLists(model, null);
            return "staff/kb";
        }
        boolean creating = form.getId() == null;
        KbArticle saved = knowledgeBaseService.saveArticle(form, principal.getUser());
        auditLogService.log(principal.getUser(), creating ? "KB_ARTICLE_CREATED" : "KB_ARTICLE_UPDATED",
                "KbArticle", saved.getId(), "\"" + saved.getTitle() + "\" (" + saved.getStatus() + ")");
        redirectAttributes.addFlashAttribute("success",
                "Article \"" + saved.getTitle() + "\" " + (creating ? "created" : "updated")
                        + " as " + saved.getStatus().name().toLowerCase() + ".");
        return "redirect:/staff/kb";
    }

    @PostMapping("/categories")
    public String createCategory(@RequestParam(required = false) String name,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            var category = knowledgeBaseService.createCategory(name);
            auditLogService.log(principal.getUser(), "KB_CATEGORY_CREATED", "KbCategory", category.getId(),
                    "\"" + category.getName() + "\"");
            redirectAttributes.addFlashAttribute("success", "Category \"" + category.getName() + "\" added.");
        } catch (BusinessRuleException | DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/kb";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        try {
            String title = knowledgeBaseService.getById(id).getTitle();
            knowledgeBaseService.deleteArticle(id);
            auditLogService.log(principal.getUser(), "KB_ARTICLE_DELETED", "KbArticle", id, "\"" + title + "\"");
            redirectAttributes.addFlashAttribute("success", "Article \"" + title + "\" removed.");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "That article no longer exists.");
        }
        return "redirect:/staff/kb";
    }

    private void fillLists(Model model, String q) {
        List<KbArticle> articles = knowledgeBaseService.allForStaff();
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase().strip();
            articles = articles.stream()
                    .filter(a -> a.getTitle().toLowerCase().contains(needle)
                            || a.getCategory().getName().toLowerCase().contains(needle))
                    .toList();
        }
        model.addAttribute("articles", articles);
        model.addAttribute("q", q);
        model.addAttribute("categories", knowledgeBaseService.listCategories());
    }
}
