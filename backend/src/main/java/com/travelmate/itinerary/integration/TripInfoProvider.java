package com.travelmate.itinerary.integration;

import java.util.List;

/**
 * Access to trips (Module 2).
 * INTEGRATION: replace MockTripInfoProvider with an implementation that calls Module 2's TripService.
 */
public interface TripInfoProvider {

    TripInfo getTrip(Long tripId);

    List<TripInfo> getTripsForUser(Long userId);
}
