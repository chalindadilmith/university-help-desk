package com.helpdesk.dto;

import com.helpdesk.entity.Priority;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RoutingRuleForm {

    @NotNull(message = "Select a category")
    private Long categoryId;

    @NotNull(message = "Select a priority")
    private Priority priority;

    @NotNull(message = "Select a target pool")
    private Long targetPoolId;
}
