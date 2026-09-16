package com.helpdesk.repository;

import com.helpdesk.entity.ClaimStatus;
import com.helpdesk.entity.LostFoundClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LostFoundClaimRepository extends JpaRepository<LostFoundClaim, Long> {

    List<LostFoundClaim> findByItemIdOrderByCreatedAtDesc(Long itemId);

    Optional<LostFoundClaim> findByItemIdAndClaimantId(Long itemId, Long claimantId);

    List<LostFoundClaim> findByStatusOrderByCreatedAtAsc(ClaimStatus status);
}
