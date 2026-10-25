package com.travelmate.itinerary.dto;

import com.travelmate.itinerary.entity.ItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalTime;

/**
 * Add or edit an item. When refId points to a catalog entry and name/unitPrice are empty,
 * they are filled from the catalog.
 */
public record ItemRequest(@NotNull ItemType type,
                          Long refId,
                          String name,
                          String category,
                          LocalTime startTime,
                          LocalTime endTime,
                          @PositiveOrZero Double unitPrice,
                          @Min(1) Integer quantity,
                          String notes) {
}
