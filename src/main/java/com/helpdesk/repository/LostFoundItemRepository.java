package com.helpdesk.repository;

import com.helpdesk.entity.ItemStatus;
import com.helpdesk.entity.ItemType;
import com.helpdesk.entity.LostFoundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LostFoundItemRepository extends JpaRepository<LostFoundItem, Long> {

    @Query("SELECT i FROM LostFoundItem i WHERE i.status = :status " +
           "AND (:itemType IS NULL OR i.itemType = :itemType) " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(i.location) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY i.createdAt DESC")
    List<LostFoundItem> search(@Param("status") ItemStatus status,
                                @Param("itemType") ItemType itemType,
                                @Param("keyword") String keyword);

    List<LostFoundItem> findByReporterIdOrderByCreatedAtDesc(Long reporterId);
}
