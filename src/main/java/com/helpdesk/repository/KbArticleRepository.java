package com.helpdesk.repository;

import com.helpdesk.entity.KbArticle;
import com.helpdesk.entity.KbArticleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

    List<KbArticle> findAllByOrderByCreatedAtDesc();

    @Query("SELECT a FROM KbArticle a WHERE a.status = :status " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(a.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY a.viewCount DESC")
    List<KbArticle> search(@Param("status") KbArticleStatus status, @Param("keyword") String keyword);

    List<KbArticle> findByStatus(KbArticleStatus status);
}
