package com.helpdesk.controller;

import com.helpdesk.dto.ArticleForm;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/** UC-11: staff create/edit/remove FAQ articles, published immediately (no approval workflow in this MVP). */
@Controller
@RequestMapping("/staff/kb")
public class StaffKbController {

    private final KnowledgeBaseService knowledgeBaseService;

    public StaffKbController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long editId, Model model) {
        model.addAttribute("articles", knowledgeBaseService.allForStaff());
        model.addAttribute("categories", knowledgeBaseService.listCategories());
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
                        @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("articles", knowledgeBaseService.allForStaff());
            model.addAttribute("categories", knowledgeBaseService.listCategories());
            return "staff/kb";
        }
        knowledgeBaseService.saveArticle(form, principal.getUser());
        return "redirect:/staff/kb";
    }

    @PostMapping("/categories")
    public String createCategory(@RequestParam String name) {
        knowledgeBaseService.createCategory(name);
        return "redirect:/staff/kb";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        knowledgeBaseService.deleteArticle(id);
        return "redirect:/staff/kb";
    }
}
