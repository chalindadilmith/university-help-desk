package com.helpdesk.service;

import com.helpdesk.dto.ReportItemForm;
import com.helpdesk.entity.ItemType;
import com.helpdesk.entity.LostFoundClaim;
import com.helpdesk.entity.LostFoundItem;
import com.helpdesk.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LostFoundService {

    /** Report a lost/found item. The form's photo is required. */
    LostFoundItem reportItem(User reporter, ReportItemForm form);

    List<LostFoundItem> search(ItemType itemType, String keyword);

    LostFoundItem getById(Long id);

    /**
     * Submit a claim (photo required). If the claimant's earlier claim on this item was
     * rejected, this resubmits it for review with the new evidence.
     */
    LostFoundClaim claimItem(User claimant, Long itemId, String proofDescription, MultipartFile evidenceImage);

    Optional<LostFoundClaim> findClaim(Long itemId, Long claimantId);

    LostFoundClaim getClaim(Long claimId);

    List<LostFoundClaim> pendingClaims();

    /**
     * The reporter edits their own report. Only while the item is ACTIVE; type, description, location and
     * date can change, and a new photo is optional (the old one is kept when none is uploaded).
     */
    LostFoundItem editReport(User actor, Long itemId, ReportItemForm form);

    /** Reports the student made, newest first. */
    List<LostFoundItem> myReports(Long userId);

    /** Claims the student made, newest first. */
    List<LostFoundClaim> myClaims(Long userId);

    /** Every report, newest first (admin moderation view). */
    List<LostFoundItem> allItems();

    /** Admin removes an inappropriate or duplicate report together with its claims, and tells the people involved. */
    void adminRemoveItem(User admin, Long itemId, String reason);

    List<LostFoundClaim> claimsForItem(Long itemId);

    /** Admin verifies a claim and sets where/when the item can be collected. */
    LostFoundClaim approveClaim(Long claimId, User admin, String location, LocalDate date, String time, String note);

    /** Admin rejects a claim; the reason is mandatory and is sent to the claimant. */
    LostFoundClaim rejectClaim(Long claimId, User admin, String reason);

    /**
     * The reporter deletes their own report (e.g. they found the item themselves).
     * All claims on it are removed and their claimants notified. Only the reporter may do this.
     */
    void deleteReport(User actor, Long itemId);

    /**
     * The claimant deletes their own claim (e.g. they met the owner directly).
     * The reporter is notified; if the claim had been verified the item is open for claims again.
     * Only the claimant may do this.
     */
    void deleteClaim(User actor, Long claimId);
}
