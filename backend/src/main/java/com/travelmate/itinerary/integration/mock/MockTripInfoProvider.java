package com.travelmate.itinerary.integration.mock;

import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.integration.TripInfoProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MockTripInfoProvider implements TripInfoProvider {

    private final MockDataStore store;

    @Override
    public TripInfo getTrip(Long tripId) {
        return store.trips().stream()
                .filter(t -> t.id().equals(tripId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Trip " + tripId + " not found"));
    }

    @Override
    public List<TripInfo> getTripsForUser(Long userId) {
        return store.trips().stream().filter(t -> t.userId().equals(userId)).toList();
    }
}
