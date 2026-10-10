package com.helpdesk.service;

import com.helpdesk.dto.ArticleForm;
import com.helpdesk.entity.KbArticle;
import com.helpdesk.entity.KbCategory;
import com.helpdesk.entity.User;

import java.util.List;

public interface KnowledgeBaseService {

    /** Published articles matching the keyword, best match first. */
    List<KbArticle> searchPublished(String keyword);

    /** Same, optionally limited to one category (categoryId may be null). */
    List<KbArticle> searchPublished(String keyword, Long categoryId);

    List<KbArticle> allForStaff();

    /** Published articles only (drafts behave as "not found") and counts a view. */
    KbArticle getByIdAndIncrementViews(Long id);

    KbArticle getById(Long id);

    /** Other published articles from the same category, most viewed first. */
    List<KbArticle> relatedArticles(KbArticle article, int limit);

    List<KbCategory> listCategories();

    KbCategory createCategory(String name);

    KbArticle saveArticle(ArticleForm form, User author);

    void deleteArticle(Long id);

    /** Records a "was this helpful?" vote on a published article. */
    void vote(Long articleId, boolean helpful);
}
