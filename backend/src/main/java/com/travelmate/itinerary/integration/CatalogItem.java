package com.travelmate.itinerary.integration;

import com.travelmate.itinerary.entity.ItemType;

/**
 * Common shape for an activity (Module 3), a restaurant (Module 4) or a hotel (Module 5).
 * price = per person for an activity, average meal for a restaurant, per night for a hotel.
 */
public record CatalogItem(Long id,
                          ItemType type,
                          String name,
                          String category,
                          double price,
                          Integer durationMinutes,
                          Double rating) {
}
