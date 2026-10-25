package com.travelmate.itinerary.repository;

import com.travelmate.itinerary.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    List<Itinerary> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Itinerary> findByTripId(Long tripId);
}
