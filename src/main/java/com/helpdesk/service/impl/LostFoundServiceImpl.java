package com.helpdesk.service.impl;

import com.helpdesk.dto.ReportItemForm;
import com.helpdesk.entity.*;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.LostFoundClaimRepository;
import com.helpdesk.repository.LostFoundItemRepository;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.LostFoundService;
import com.helpdesk.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LostFoundServiceImpl implements LostFoundService {

    private final LostFoundItemRepository itemRepository;
    private final LostFoundClaimRepository claimRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public LostFoundServiceImpl(LostFoundItemRepository itemRepository,
                                 LostFoundClaimRepository claimRepository,
                                 AuditLogService auditLogService,
                                 NotificationService notificationService) {
        this.itemRepository = itemRepository;
        this.claimRepository = claimRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public LostFoundItem reportItem(User reporter, ReportItemForm form) {
        LostFoundItem item = LostFoundItem.builder()
                .reporter(reporter)
                .itemType(form.getItemType())
                .description(form.getDescription())
                .location(form.getLocation())
                .itemDate(form.getItemDate())
                .status(ItemStatus.ACTIVE)
                .build();
        return itemRepository.save(item);
    }

    @Override
    public List<LostFoundItem> search(ItemType itemType, String keyword) {
        return itemRepository.search(ItemStatus.ACTIVE, itemType, keyword);
    }

    @Override
    public LostFoundItem getById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lost & Found item not found: " + id));
    }

    @Override
    @Transactional
    public LostFoundClaim claimItem(User claimant, Long itemId) {
        LostFoundItem item = getById(itemId);

        claimRepository.findByItemIdAndClaimantId(itemId, claimant.getId()).ifPresent(existing -> {
            throw new IllegalStateException("You have already submitted a claim for this item.");
        });

        LostFoundClaim claim = LostFoundClaim.builder()
                .item(item)
                .claimant(claimant)
                .status(ClaimStatus.PENDING)
                .build();
        LostFoundClaim saved = claimRepository.save(claim);

        notificationService.notify(item.getReporter(), "LOST_FOUND_CLAIM",
                claimant.getFullName() + " submitted a claim on your " + item.getItemType() + " item report.",
                "LostFoundItem", item.getId());

        return saved;
    }

    @Override
    public List<LostFoundClaim> pendingClaims() {
        return claimRepository.findByStatusOrderByCreatedAtAsc(ClaimStatus.PENDING);
    }

    @Override
    public List<LostFoundClaim> claimsForItem(Long itemId) {
        return claimRepository.findByItemIdOrderByCreatedAtDesc(itemId);
    }

    @Override
    @Transactional
    public LostFoundClaim decideClaim(Long claimId, boolean approve, User actingAdmin) {
        LostFoundClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));

        claim.setStatus(approve ? ClaimStatus.VERIFIED : ClaimStatus.REJECTED);

        if (approve) {
            LostFoundItem item = claim.getItem();
            item.setStatus(ItemStatus.CLAIMED);
        }

        auditLogService.log(actingAdmin, approve ? "VERIFY_CLAIM" : "REJECT_CLAIM", "LostFoundClaim", claimId, null);

        notificationService.notify(claim.getClaimant(), "LOST_FOUND_CLAIM",
                "Your claim on \"" + claim.getItem().getDescription() + "\" was " +
                        (approve ? "verified. Please contact the help desk to arrange handover." : "rejected."),
                "LostFoundItem", claim.getItem().getId());

        return claim;
    }
}
