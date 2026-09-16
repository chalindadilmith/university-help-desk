package com.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PoolForm {

    @NotBlank(message = "Pool name is required")
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;
}
