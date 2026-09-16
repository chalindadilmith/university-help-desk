package com.helpdesk.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Table: notifications
 * A per-user notification feed entry, triggered by ticket status changes,
 * KB/announcement events, etc. (UC-F5-01 step 1). relatedEntityType/Id let
 * the UI link "Your ticket TCK-000123 was resolved" back to that ticket
 * without a hard foreign key to every possible source table.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient_read", columnList = "recipient_id, is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @NotBlank
    @Size(max = 50)
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @NotBlank
    @Size(max = 500)
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
