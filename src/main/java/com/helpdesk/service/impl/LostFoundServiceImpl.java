package com.helpdesk.service.impl;

import com.helpdesk.dto.ReportItemForm;
import com.helpdesk.entity.ClaimStatus;
import com.helpdesk.entity.ItemStatus;
import com.helpdesk.entity.ItemType;
import com.helpdesk.entity.LostFoundClaim;
import com.helpdesk.entity.LostFoundItem;
import com.helpdesk.entity.User;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.LostFoundClaimRepository;
import com.helpdesk.repository.LostFoundItemRepository;
import com.helpdesk.service.AuditLogService;
import com.helpdesk.service.EmailService;
import com.helpdesk.service.LostFoundService;
import com.helpdesk.service.NotificationService;
import com.helpdesk.service.ProofStorageService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LostFoundServiceImpl implements LostFoundService {

    private static final String TYPE = "LOST_FOUND_CLAIM";
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final LostFoundItemRepository itemRepository;
    private final LostFoundClaimRepository claimRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ProofStorageService proofStorageService;
    private final EmailService emailService;

    public LostFoundServiceImpl(LostFoundItemRepository itemRepository,
                                LostFoundClaimRepository claimRepository,
                                AuditLogService auditLogService,
                                NotificationService notificationService,
                                ProofStorageService proofStorageService,
                                EmailService emailService) {
        this.itemRepository = itemRepository;
        this.claimRepository = claimRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.proofStorageService = proofStorageService;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public LostFoundItem reportItem(User reporter, ReportItemForm form) {
        String stored = proofStorageService.storeImage(form.getImage()); // photo is mandatory
        LostFoundItem item = LostFoundItem.builder()
                .reporter(reporter)
                .itemType(form.getItemType())
                .description(form.getDescription().strip())
                .location(form.getLocation().strip())
                .itemDate(form.getItemDate())
                .status(ItemStatus.ACTIVE)
                .imageFileName(originalName(form.getImage(), "item-photo"))
                .imageFilePath(stored)
                .build();
        LostFoundItem saved = itemRepository.save(item);
        auditLogService.log(reporter, "REPORT_LOST_FOUND_ITEM", "LostFoundItem", saved.getId(),
                saved.getItemType() + ": " + clip(saved.getDescription(), 80));
        return saved;
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
    public LostFoundClaim claimItem(User claimant, Long itemId, String proofDescription, MultipartFile evidenceImage) {
        LostFoundItem item = getById(itemId);
        if (item.getStatus() != ItemStatus.ACTIVE) {
            throw new IllegalStateException("This item is no longer open for claims.");
        }
        if (item.getReporter().getId().equals(claimant.getId())) {
            throw new IllegalStateException("You cannot claim an item that you reported yourself.");
        }
        Optional<LostFoundClaim> existing = claimRepository.findByItemIdAndClaimantId(itemId, claimant.getId());
        boolean resubmit = existing.isPresent();
        if (resubmit && existing.get().getStatus() != ClaimStatus.REJECTED) {
            throw new IllegalStateException("You have already submitted a claim for this item.");
        }

        String description = proofDescription == null ? "" : proofDescription.trim();
        int minLength = resubmit ? 20 : 10;
        if (description.length() < minLength) {
            throw new BusinessRuleException(resubmit
                    ? "Please give more detail this time (at least 20 characters): colour, brand, marks, contents, serial number, where and when you lost it."
                    : "Please describe the item or your proof of ownership (at least 10 characters).");
        }
        if (description.length() > 1000) {
            throw new BusinessRuleException("The description must be 1000 characters or fewer.");
        }
        if (evidenceImage == null || evidenceImage.isEmpty()) {
            throw new BusinessRuleException("Please upload a photo of the item as evidence.");
        }
        String stored = proofStorageService.storeImage(evidenceImage);
        String originalName = originalName(evidenceImage, "evidence");

        LostFoundClaim claim;
        if (resubmit) {
            claim = existing.get();
            auditLogService.log(claimant, "RESUBMIT_CLAIM", "LostFoundClaim", claim.getId(),
                    "Previous rejection reason: " + clip(claim.getRejectionReason(), 300)
                            + " | previous evidence file: " + claim.getProofFilePath());
            claim.setProofDescription(description);
            claim.setProofFileName(originalName);
            claim.setProofFilePath(stored);
            claim.setStatus(ClaimStatus.PENDING);
            claim.setResubmitCount(claim.getResubmissions() + 1);
            // rejectionReason/reviewedBy stay so the admin can see why the last attempt failed
        } else {
            claim = LostFoundClaim.builder()
                    .item(item)
                    .claimant(claimant)
                    .status(ClaimStatus.PENDING)
                    .proofDescription(description)
                    .proofFileName(originalName)
                    .proofFilePath(stored)
                    .build();
        }
        LostFoundClaim saved = claimRepository.save(claim);
        auditLogService.log(claimant, resubmit ? "CLAIM_RESUBMITTED" : "SUBMIT_CLAIM", "LostFoundClaim", saved.getId(),
                "Item " + item.getId() + ": " + clip(item.getDescription(), 80));

        notificationService.notify(claimant, TYPE,
                "Your " + (resubmit ? "resubmitted claim " : "claim ") + saved.getReferenceNo() + " on \""
                        + clip(item.getDescription(), 80) + "\" has been submitted and is waiting for admin verification.",
                "LostFoundItem", item.getId());
        notificationService.notify(item.getReporter(), TYPE,
                claimant.getFullName() + " submitted a claim on your " + item.getItemType() + " item report. An admin will review it.",
                "LostFoundItem", item.getId());
        return saved;
    }

    @Override
    public Optional<LostFoundClaim> findClaim(Long itemId, Long claimantId) {
        return claimRepository.findByItemIdAndClaimantId(itemId, claimantId);
    }

    @Override
    public LostFoundClaim getClaim(Long claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));
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
    public LostFoundClaim approveClaim(Long claimId, User admin, String location, LocalDate date, String time, String note) {
        LostFoundClaim claim = getClaim(claimId);
        requirePending(claim);
        // lock the item row: two admins approving at once cannot both succeed
        LostFoundItem item = itemRepository.lockById(claim.getItem().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lost & Found item not found."));
        if (item.getStatus() != ItemStatus.ACTIVE) {
            throw new BusinessRuleException("This item has already been handed over to another claimant.");
        }
        if (date == null) {
            throw new BusinessRuleException("Please choose the collection date.");
        }
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessRuleException("The collection date cannot be in the past.");
        }
        String place = StringUtils.hasText(location) ? location.trim() : "University Help Desk";
        String when = StringUtils.hasText(time) ? time.trim() : "During help desk opening hours";
        String extra = StringUtils.hasText(note) ? note.trim() : null;
        if (place.length() > 200 || when.length() > 100 || (extra != null && extra.length() > 500)) {
            throw new BusinessRuleException("Collection details are too long (place 200, time 100, note 500 characters).");
        }

        claim.setStatus(ClaimStatus.VERIFIED);
        claim.setCollectionLocation(place);
        claim.setCollectionDate(date);
        claim.setCollectionTime(when);
        claim.setCollectionNote(extra);
        claim.setRejectionReason(null);
        claim.setReviewedBy(admin);
        claim.setReviewedAt(java.time.LocalDateTime.now());
        item.setStatus(ItemStatus.CLAIMED);
        auditLogService.log(admin, "VERIFY_CLAIM", "LostFoundClaim", claimId,
                claim.getReferenceNo() + " - collect at " + place + " on " + date + ", " + when);

        User claimant = claim.getClaimant();
        String ref = claim.getReferenceNo();
        notificationService.notify(claimant, TYPE,
                clip("Your claim " + ref + " on \"" + clip(item.getDescription(), 60) + "\" is VERIFIED. Collect it from: "
                        + place + " on " + date.format(LONG_DATE) + ", " + when + ". Bring your Student ID and reference no. " + ref + "."
                        + (extra == null ? "" : " Note: " + extra), 500),
                "LostFoundItem", item.getId());
        emailService.sendAsync(claimant.getEmail(), "Lost & Found claim verified - " + ref,
                "Hello " + claimant.getFullName() + ",\n\n"
                        + "Your Lost & Found claim has been verified successfully.\n\n"
                        + "Reference No: " + ref + "\n"
                        + "Item: " + item.getDescription() + "\n\n"
                        + "Please collect your item from:\n"
                        + "Place: " + place + "\n"
                        + "Date: " + date.format(LONG_DATE) + "\n"
                        + "Time: " + when + "\n"
                        + (extra == null ? "" : "Note: " + extra + "\n")
                        + "\nPlease bring your Student ID"
                        + (claimant.getStudentCode() != null ? " (" + claimant.getStudentCode() + ")" : "")
                        + " and the reference number above.\n\n"
                        + "University Help Desk");

        notificationService.notify(item.getReporter(), TYPE,
                "The " + item.getItemType() + " item you reported (\"" + clip(item.getDescription(), 80)
                        + "\") was verified as belonging to " + claimant.getFullName() + " and will be handed over via the help desk.",
                "LostFoundItem", item.getId());

        // Only one claimant can receive the item: close every other pending claim on it.
        for (LostFoundClaim other : claimRepository.findByItemIdAndStatus(item.getId(), ClaimStatus.PENDING)) {
            if (other.getId().equals(claim.getId())) continue;
            other.setStatus(ClaimStatus.REJECTED);
            other.setRejectionReason("The item was handed to another verified claimant.");
            other.setReviewedBy(admin);
            other.setReviewedAt(java.time.LocalDateTime.now());
            auditLogService.log(admin, "AUTO_REJECT_CLAIM", "LostFoundClaim", other.getId(),
                    "Another claim on the same item was verified");
            notificationService.notify(other.getClaimant(), TYPE,
                    "Your claim " + other.getReferenceNo() + " on \"" + clip(item.getDescription(), 80)
                            + "\" was rejected because the item was handed to another verified claimant.",
                    "LostFoundItem", item.getId());
        }
        return claim;
    }

    @Override
    @Transactional
    public LostFoundClaim rejectClaim(Long claimId, User admin, String reason) {
        LostFoundClaim claim = getClaim(claimId);
        requirePending(claim);
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 10) {
            throw new BusinessRuleException("Please give a clear reason (at least 10 characters) so the student knows what evidence to improve.");
        }
        if (why.length() > 1000) {
            throw new BusinessRuleException("The reason must be 1000 characters or fewer.");
        }
        claim.setStatus(ClaimStatus.REJECTED);
        claim.setRejectionReason(why);
        claim.setReviewedBy(admin);
        claim.setReviewedAt(java.time.LocalDateTime.now());
        auditLogService.log(admin, "REJECT_CLAIM", "LostFoundClaim", claimId, claim.getReferenceNo() + " - " + clip(why, 300));

        LostFoundItem item = claim.getItem();
        notificationService.notify(claim.getClaimant(), TYPE,
                clip("Your claim " + claim.getReferenceNo() + " on \"" + clip(item.getDescription(), 50) + "\" was rejected. Reason: "
                        + clip(why, 230) + " You can reapply from the item page with stronger evidence (a clearer photo and more identifying details).", 500),
                "LostFoundItem", item.getId());
        return claim;
    }

    @Override
    @Transactional
    public void deleteReport(User actor, Long itemId) {
        // lock the row so a claim being verified at the same moment cannot race the delete
        LostFoundItem item = itemRepository.lockById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Lost & Found item not found: " + itemId));
        if (!item.getReporter().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You can only delete reports that you made yourself.");
        }
        String summary = item.getItemType() + ": " + clip(item.getDescription(), 80);
        List<String> filesToRemove = new ArrayList<>();
        filesToRemove.add(item.getImageFilePath());

        List<LostFoundClaim> claims = claimRepository.findByItemIdOrderByCreatedAtDesc(itemId);
        for (LostFoundClaim claim : claims) {
            if (claim.getStatus() != ClaimStatus.REJECTED) {
                notificationService.notify(claim.getClaimant(), TYPE,
                        clip("Your claim " + claim.getReferenceNo() + " on \"" + clip(item.getDescription(), 80)
                                + "\" was closed because the reporter removed the report (the item has been sorted out).", 500),
                        null, null);
            }
            filesToRemove.add(claim.getProofFilePath());
            auditLogService.log(actor, "DELETE_CLAIM_WITH_REPORT", "LostFoundClaim", claim.getId(),
                    claim.getReferenceNo() + " (" + claim.getStatus() + ") removed with its report");
        }
        // claims first: they reference the item
        claimRepository.deleteAll(claims);
        itemRepository.delete(item);
        itemRepository.flush();

        auditLogService.log(actor, "DELETE_LOST_FOUND_REPORT", "LostFoundItem", itemId,
                summary + " | " + claims.size() + " claim(s) removed");
        removeFilesAfterCommit(filesToRemove);
    }

    @Override
    @Transactional
    public LostFoundItem editReport(User actor, Long itemId, ReportItemForm form) {
        // lock the row so a claim being verified at the same moment cannot race the edit
        LostFoundItem item = itemRepository.lockById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Lost & Found item not found: " + itemId));
        if (!item.getReporter().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You can only edit reports that you made yourself.");
        }
        if (item.getStatus() != ItemStatus.ACTIVE) {
            throw new BusinessRuleException("This report can no longer be edited because the item has been "
                    + (item.getStatus() == ItemStatus.CLAIMED ? "handed over." : "archived."));
        }
        String description = form.getDescription() == null ? "" : form.getDescription().strip();
        String location = form.getLocation() == null ? "" : form.getLocation().strip();
        if (description.isEmpty() || location.isEmpty() || form.getItemType() == null || form.getItemDate() == null) {
            throw new BusinessRuleException("Type, description, location and date are all required.");
        }

        String oldPhoto = null;
        boolean newPhoto = form.getImage() != null && !form.getImage().isEmpty();
        if (newPhoto) {
            String stored = proofStorageService.storeImage(form.getImage());
            oldPhoto = item.getImageFilePath();
            item.setImageFilePath(stored);
            item.setImageFileName(originalName(form.getImage(), "item-photo"));
        }
        item.setItemType(form.getItemType());
        item.setDescription(description);
        item.setLocation(location);
        item.setItemDate(form.getItemDate());

        auditLogService.log(actor, "EDIT_LOST_FOUND_REPORT", "LostFoundItem", itemId,
                clip(item.getItemType() + ": " + description + (newPhoto ? " (photo replaced)" : ""), 500));
        for (LostFoundClaim claim : claimRepository.findByItemIdOrderByCreatedAtDesc(itemId)) {
            if (claim.getStatus() == ClaimStatus.PENDING) {
                notificationService.notify(claim.getClaimant(), TYPE,
                        clip("The reporter updated the details of the item you claimed (" + clip(description, 80)
                                + "). Your claim " + claim.getReferenceNo() + " is still waiting for admin review.", 500),
                        "LostFoundItem", itemId);
            }
        }
        if (oldPhoto != null) {
            removeFilesAfterCommit(java.util.Collections.singletonList(oldPhoto));
        }
        return item;
    }

    @Override
    public List<LostFoundItem> myReports(Long userId) {
        return itemRepository.findByReporterIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<LostFoundClaim> myClaims(Long userId) {
        return claimRepository.findByClaimantIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<LostFoundItem> allItems() {
        return itemRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    @Override
    @Transactional
    public void adminRemoveItem(User admin, Long itemId, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 5) {
            throw new BusinessRuleException("Please give a short reason (at least 5 characters).");
        }
        if (why.length() > 300) {
            throw new BusinessRuleException("The reason must be 300 characters or fewer.");
        }
        LostFoundItem item = itemRepository.lockById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Lost & Found item not found: " + itemId));
        String summary = item.getItemType() + ": " + clip(item.getDescription(), 80);
        List<String> filesToRemove = new ArrayList<>();
        filesToRemove.add(item.getImageFilePath());

        notificationService.notify(item.getReporter(), TYPE,
                clip("Your Lost & Found report \"" + clip(item.getDescription(), 80) + "\" was removed by an administrator. Reason: " + why, 500),
                null, null);
        List<LostFoundClaim> claims = claimRepository.findByItemIdOrderByCreatedAtDesc(itemId);
        for (LostFoundClaim claim : claims) {
            if (claim.getStatus() != ClaimStatus.REJECTED) {
                notificationService.notify(claim.getClaimant(), TYPE,
                        clip("Your claim " + claim.getReferenceNo() + " was closed because the report was removed by an administrator.", 500),
                        null, null);
            }
            filesToRemove.add(claim.getProofFilePath());
        }
        claimRepository.deleteAll(claims);
        itemRepository.delete(item);
        itemRepository.flush();

        auditLogService.log(admin, "ADMIN_REMOVE_LOST_FOUND_REPORT", "LostFoundItem", itemId,
                clip(summary + " | " + claims.size() + " claim(s) removed | " + why, 500));
        removeFilesAfterCommit(filesToRemove);
    }

    @Override
    @Transactional
    public void deleteClaim(User actor, Long claimId) {
        LostFoundClaim claim = getClaim(claimId);
        if (!claim.getClaimant().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You can only delete claims that you made yourself.");
        }
        LostFoundItem item = itemRepository.lockById(claim.getItem().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lost & Found item not found."));
        ClaimStatus previous = claim.getStatus();
        String ref = claim.getReferenceNo();
        String proof = claim.getProofFilePath();

        // A verified claim is what marked the item as handed over; without it the item is open again.
        if (previous == ClaimStatus.VERIFIED && item.getStatus() == ItemStatus.CLAIMED) {
            item.setStatus(ItemStatus.ACTIVE);
        }
        claimRepository.delete(claim);
        claimRepository.flush();

        auditLogService.log(actor, "DELETE_CLAIM", "LostFoundClaim", claimId,
                ref + " (" + previous + ") withdrawn by the claimant on item " + item.getId());
        notificationService.notify(item.getReporter(), TYPE,
                clip(actor.getFullName() + " withdrew their claim " + ref + " on your "
                        + item.getItemType() + " item (\"" + clip(item.getDescription(), 60) + "\")."
                        + (previous == ClaimStatus.VERIFIED
                        ? " If the item has already been handed over, you can delete your report from its page."
                        : ""), 500),
                "LostFoundItem", item.getId());
        removeFilesAfterCommit(List.of(proof == null ? "" : proof));
    }

    /** Delete photo files only once the database delete has really committed. */
    private void removeFilesAfterCommit(List<String> storedNames) {
        Runnable cleanup = () -> storedNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .forEach(proofStorageService::delete);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private static void requirePending(LostFoundClaim claim) {
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new BusinessRuleException("This claim has already been " + claim.getStatus().name().toLowerCase() + ".");
        }
    }

    private static String originalName(MultipartFile file, String fallback) {
        String name = StringUtils.cleanPath(file.getOriginalFilename() == null ? fallback : file.getOriginalFilename());
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }
}
