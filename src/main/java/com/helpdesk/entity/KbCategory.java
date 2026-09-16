package com.helpdesk.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Table: kb_categories - groups FAQ/knowledge-base articles (UC-F4-01). */
@Entity
@Table(name = "kb_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
}
