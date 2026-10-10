package com.helpdesk.service.impl;

import com.helpdesk.dto.ArticleForm;
import com.helpdesk.entity.KbArticle;
import com.helpdesk.entity.KbArticleStatus;
import com.helpdesk.entity.KbCategory;
import com.helpdesk.entity.User;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.KbArticleRepository;
import com.helpdesk.repository.KbCategoryRepository;
import com.helpdesk.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "can", "how", "what", "when", "where", "why", "who", "does", "did",
            "was", "were", "with", "from", "that", "this", "have", "has", "you", "your", "our", "not",
            "but", "any", "get", "i'm", "its", "into", "about", "need", "want", "please", "help");

    private final KbArticleRepository articleRepository;
    private final KbCategoryRepository categoryRepository;
    /** Strategy pattern: how an article is scored against a search (chosen by app.kb.ranking). */
    private final com.helpdesk.strategy.search.KbSearchRanker ranker;

    public KnowledgeBaseServiceImpl(KbArticleRepository articleRepository, KbCategoryRepository categoryRepository,
                                    com.helpdesk.strategy.search.KbSearchRanker ranker) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.ranker = ranker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KbArticle> searchPublished(String keyword) {
        return searchPublished(keyword, null);
    }

    /**
     * Word-based search: "reset my password" finds "Password reset".
     * Ranking: whole phrase in title > words in title > words in content, then most viewed.
     * Articles matching none of the words are dropped.
     */
    @Override
    @Transactional(readOnly = true)
    public List<KbArticle> searchPublished(String keyword, Long categoryId) {
        List<KbArticle> pool = new ArrayList<>(articleRepository.findByStatus(KbArticleStatus.PUBLISHED));
        if (categoryId != null) {
            pool.removeIf(a -> !categoryId.equals(a.getCategory().getId()));
        }
        String phrase = keyword == null ? "" : keyword.toLowerCase().replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ").trim();
        if (phrase.isEmpty()) {
            pool.sort(Comparator.comparingInt(KbArticle::getViewCount).reversed());
            return pool;
        }
        Set<String> words = new LinkedHashSet<>();
        for (String w : phrase.split(" ")) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) words.add(w);
        }
        if (words.isEmpty()) words.add(phrase); // e.g. a short query like "wifi" is kept as-is

        record Scored(KbArticle article, int score) {}
        List<Scored> scored = new ArrayList<>();
        com.helpdesk.strategy.search.SearchTerms terms = new com.helpdesk.strategy.search.SearchTerms(phrase, words);
        for (KbArticle a : pool) {
            int score = ranker.score(a, terms);
            if (score > 0) scored.add(new Scored(a, score));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.article().getViewCount(), Comparator.reverseOrder()));
        return scored.stream().map(Scored::article).toList();
    }

    @Override
    public List<KbArticle> allForStaff() {
        return articleRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public KbArticle getByIdAndIncrementViews(Long id) {
        KbArticle article = getById(id);
        if (article.getStatus() != KbArticleStatus.PUBLISHED) {
            // drafts are invisible to students and never counted
            throw new ResourceNotFoundException("Article not found: " + id);
        }
        article.setViewCount(article.getViewCount() + 1);
        return article;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void vote(Long articleId, boolean helpful) {
        KbArticle article = getById(articleId);
        if (article.getStatus() != KbArticleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Article not found: " + articleId);
        }
        if (helpful) {
            articleRepository.addHelpful(articleId);
        } else {
            articleRepository.addNotHelpful(articleId);
        }
    }

    @Override
    public KbArticle getById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KbArticle> relatedArticles(KbArticle article, int limit) {
        return articleRepository.findByStatus(KbArticleStatus.PUBLISHED).stream()
                .filter(a -> !a.getId().equals(article.getId()))
                .filter(a -> a.getCategory().getId().equals(article.getCategory().getId()))
                .sorted(Comparator.comparingInt(KbArticle::getViewCount).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<KbCategory> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional
    public KbCategory createCategory(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new BusinessRuleException("Category name cannot be empty.");
        }
        if (clean.length() > 100) {
            throw new BusinessRuleException("Category name must be 100 characters or fewer.");
        }
        if (categoryRepository.existsByNameIgnoreCase(clean)) {
            throw new DuplicateResourceException("name", "A KB category with this name already exists.");
        }
        return categoryRepository.save(KbCategory.builder().name(clean).build());
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
        article.setTitle(form.getTitle().strip());
        article.setContent(form.getContent().strip());
        article.setCategory(category);
        article.setStatus(form.getStatus());
        return articleRepository.save(article);
    }

    @Override
    @Transactional
    public void deleteArticle(Long id) {
        KbArticle article = getById(id); // friendly 404 instead of a raw EmptyResultDataAccessException
        articleRepository.delete(article);
    }
}
