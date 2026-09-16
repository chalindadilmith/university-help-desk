package com.helpdesk.service;

import com.helpdesk.dto.ArticleForm;
import com.helpdesk.entity.KbArticle;
import com.helpdesk.entity.KbCategory;
import com.helpdesk.entity.User;

import java.util.List;

public interface KnowledgeBaseService {

    List<KbArticle> searchPublished(String keyword);

    List<KbArticle> allForStaff();

    KbArticle getByIdAndIncrementViews(Long id);

    KbArticle getById(Long id);

    List<KbCategory> listCategories();

    KbCategory createCategory(String name);

    KbArticle saveArticle(ArticleForm form, User author);

    void deleteArticle(Long id);
}
