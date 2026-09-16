package com.helpdesk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Table: routing_rules
 * Defines that "a ticket in category X with priority Y goes to pool Z"
 * (UC-F3-01 step 4). The unique constraint on (category, priority) is the
 * database-level implementation of the documented "conflict detection"
 * requirement (UC-F3-01 extension 4a) - you cannot save two rules for the
 * same category+priority combination.
 */
@Entity
@Table(name = "routing_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_routing_category_priority", columnNames = {"category_id", "priority"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_pool_id", nullable = false)
    private JobPool targetPool;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
