package com.travelmate.itinerary.repository;

import com.travelmate.itinerary.entity.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, Long> {
}
