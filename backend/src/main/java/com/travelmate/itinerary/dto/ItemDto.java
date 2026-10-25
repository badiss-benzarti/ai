package com.travelmate.itinerary.dto;

import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.entity.ItineraryItem;

import java.time.LocalTime;

public record ItemDto(Long id,
                      ItemType type,
                      Long refId,
                      String name,
                      String category,
                      LocalTime startTime,
                      LocalTime endTime,
                      double unitPrice,
                      int quantity,
                      double estimatedCost,
                      String notes) {

    public static ItemDto from(ItineraryItem i) {
        return new ItemDto(i.getId(), i.getType(), i.getRefId(), i.getName(), i.getCategory(),
                i.getStartTime(), i.getEndTime(), i.getUnitPrice(), i.getQuantity(),
                i.getEstimatedCost(), i.getNotes());
    }
}
