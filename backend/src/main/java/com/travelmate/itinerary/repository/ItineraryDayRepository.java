package com.travelmate.itinerary.repository;

import com.travelmate.itinerary.entity.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, Long> {
}
