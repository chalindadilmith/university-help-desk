package com.helpdesk.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Table: ticket_categories
 * e.g. IT, Academic, Facilities (UC-F2-01 step 1, UC-F3-01 step 2).
 * Each category is (eventually) linked to one staff work-pool that handles it.
 */
@Entity
@Table(name = "ticket_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCategory {

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

    /** May be null until an Admin assigns this category to a work-pool. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id")
    private JobPool pool;
}
