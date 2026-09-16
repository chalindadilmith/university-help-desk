package com.helpdesk.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Table: job_pools
 * A "staff work-pool" - a named group of Help Desk Staff who handle certain
 * ticket categories (UC-F3-01: "groups categories into staff work-pools").
 * NOTE: this is ticket-routing only. The unrelated "student job/career
 * listings" reading of UC-10 was explicitly dropped from scope (Phase 0,
 * Critical Issue 1) - do not confuse this table with a job-postings board.
 */
@Entity
@Table(name = "job_pools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Size(max = 255)
    @Column(name = "description", length = 255)
    private String description;

    /** Staff members who belong to this pool (M-M, join table pool_staff). */
    @ManyToMany
    @JoinTable(
            name = "pool_staff",
            joinColumns = @JoinColumn(name = "pool_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> staffMembers = new HashSet<>();
}
