package com.travelmate.itinerary.dto;

import com.travelmate.itinerary.entity.ItemType;

/** One swap made by the budget optimizer. */
public record ReplacementDto(Long itemId,
                             int dayNumber,
                             ItemType type,
                             String oldName,
                             String newName,
                             double oldCost,
                             double newCost,
                             String reason) {
}
