package com.travelmate.itinerary.service;

import com.travelmate.itinerary.integration.CatalogItem;

import java.time.LocalTime;
import java.util.List;

/** Result of the planner, before it is saved as entities. */
public record ItineraryPlan(CatalogItem hotel, List<Day> days, boolean aiUsed) {

    public record Day(int dayNumber, String title, List<Slot> slots) {
    }

    public record Slot(CatalogItem item, LocalTime start, LocalTime end, String note) {
    }
}
