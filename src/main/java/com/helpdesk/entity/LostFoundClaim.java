package com.helpdesk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table: lost_found_claims
 * Someone claims a listed item; System Admin verifies ownership before handover
 * (UC-F1-01 step 9). Unique (item, claimant) so the same user can't file
 * duplicate claims on the same item.
 */
@Entity
@Table(name = "lost_found_claims", uniqueConstraints = {
        @UniqueConstraint(name = "uk_claim_item_claimant", columnNames = {"item_id", "claimant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LostFoundClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private LostFoundItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.PENDING;

    /** Claimant's description of the item / proof of ownership, for the admin to verify. */
    @Column(name = "proof_description", length = 1000)
    private String proofDescription;

    /** Original name of the claimant's evidence photo (shown to the admin). */
    @Column(name = "proof_file_name", length = 255)
    private String proofFileName;

    /** Stored (server-generated) file name of the optional proof upload. */
    @Column(name = "proof_file_path", length = 500)
    private String proofFilePath;

    /** Why the admin rejected the claim (shown to the claimant, who may then resubmit). */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** How many times the claimant has resubmitted after a rejection (null = never). */
    @Column(name = "resubmit_count")
    private Integer resubmitCount;

    // Collection details, filled in by the admin when the claim is verified.
    @Column(name = "collection_location", length = 200)
    private String collectionLocation;

    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Column(name = "collection_time", length = 100)
    private String collectionTime;

    @Column(name = "collection_note", length = 500)
    private String collectionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Reference number shown to students and admins, e.g. LF-2026-00012 (derived, not stored). */
    public String getReferenceNo() {
        if (id == null || createdAt == null) return null;
        return String.format("LF-%d-%05d", createdAt.getYear(), id);
    }

    public int getResubmissions() {
        return resubmitCount == null ? 0 : resubmitCount;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
