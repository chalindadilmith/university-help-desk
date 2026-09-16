package com.helpdesk.service.impl;

import com.helpdesk.dto.ArticleForm;
import com.helpdesk.entity.KbArticle;
import com.helpdesk.entity.KbArticleStatus;
import com.helpdesk.entity.KbCategory;
import com.helpdesk.entity.User;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.KbArticleRepository;
import com.helpdesk.repository.KbCategoryRepository;
import com.helpdesk.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KbArticleRepository articleRepository;
    private final KbCategoryRepository categoryRepository;

    public KnowledgeBaseServiceImpl(KbArticleRepository articleRepository, KbCategoryRepository categoryRepository) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<KbArticle> searchPublished(String keyword) {
        return articleRepository.search(KbArticleStatus.PUBLISHED, keyword);
    }

    @Override
    public List<KbArticle> allForStaff() {
        return articleRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public KbArticle getByIdAndIncrementViews(Long id) {
        KbArticle article = getById(id);
        article.setViewCount(article.getViewCount() + 1);
        return article;
    }

    @Override
    public KbArticle getById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));
    }

    @Override
    public List<KbCategory> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional
    public KbCategory createCategory(String name) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("name", "A KB category with this name already exists.");
        }
        return categoryRepository.save(KbCategory.builder().name(name).build());
    }

    @Override
    @Transactional
    public KbArticle saveArticle(ArticleForm form, User author) {
        KbCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("KB category not found: " + form.getCategoryId()));

        KbArticle article;
        if (form.getId() != null) {
            article = getById(form.getId());
        } else {
            article = KbArticle.builder().author(author).viewCount(0).build();
        }
        article.setTitle(form.getTitle());
        article.setContent(form.getContent());
        article.setCategory(category);
        article.setStatus(form.getStatus());
        return articleRepository.save(article);
    }

    @Override
    @Transactional
    public void deleteArticle(Long id) {
        articleRepository.deleteById(id);
    }
}
