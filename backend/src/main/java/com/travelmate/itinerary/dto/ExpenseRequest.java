package com.travelmate.itinerary.dto;

import com.travelmate.itinerary.entity.ItemType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record ExpenseRequest(@NotNull ItemType category,
                             @NotNull @Positive Double amount,
                             LocalDate date,
                             String description) {
}
