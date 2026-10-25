package com.travelmate.itinerary.dto;

import com.travelmate.itinerary.entity.ItineraryDay;
import com.travelmate.itinerary.entity.ItineraryItem;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record DayDto(Long id,
                     int dayNumber,
                     LocalDate date,
                     String title,
                     double dayCost,
                     List<ItemDto> items) {

    public static DayDto from(ItineraryDay day) {
        return new DayDto(day.getId(), day.getDayNumber(), day.getDate(), day.getTitle(),
                day.getItems().stream().mapToDouble(ItineraryItem::getEstimatedCost).sum(),
                day.getItems().stream()
                        .sorted(Comparator.comparing(ItineraryItem::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(ItemDto::from).toList());
    }
}
