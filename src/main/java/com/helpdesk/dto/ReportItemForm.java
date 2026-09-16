package com.helpdesk.dto;

import com.helpdesk.entity.ItemType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ReportItemForm {

    @NotNull(message = "Select whether this is a lost or found item")
    private ItemType itemType;

    @NotBlank(message = "Description is required")
    @Size(max = 500)
    private String description;

    @NotBlank(message = "Location is required")
    @Size(max = 150)
    private String location;

    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDate itemDate;
}
