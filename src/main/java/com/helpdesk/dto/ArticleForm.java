package com.helpdesk.dto;

import com.helpdesk.entity.KbArticleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ArticleForm {

    private Long id; // null when creating

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Select a category")
    private Long categoryId;

    @NotNull
    private KbArticleStatus status = KbArticleStatus.PUBLISHED;
}
