package com.travelmate.itinerary.integration.mock;

import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.TripInfo;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads src/main/resources/mock/mock-data.json.
 * Temporary: delete this package once Modules 2-5 are integrated.
 */
@Component
public class MockDataStore {

    private final MockData data;

    public MockDataStore(JsonMapper jsonMapper) throws IOException {
        try (InputStream in = new ClassPathResource("mock/mock-data.json").getInputStream()) {
            this.data = jsonMapper.readValue(in, MockData.class);
        }
    }

    public List<TripInfo> trips() {
        return data.trips();
    }

    public List<CatalogItem> items(ItemType type, String destination) {
        List<RawItem> source = switch (type) {
            case ACTIVITY -> data.activities();
            case RESTAURANT -> data.restaurants();
            case HOTEL -> data.hotels();
            default -> List.of();
        };
        return source.stream()
                .filter(r -> r.destination().equalsIgnoreCase(destination))
                .map(r -> new CatalogItem(r.id(), type, r.name(), r.category(), r.price(), r.durationMinutes(), r.rating()))
                .toList();
    }

    record MockData(List<TripInfo> trips, List<RawItem> activities, List<RawItem> restaurants, List<RawItem> hotels) {}

    record RawItem(Long id, String destination, String name, String category,
                   double price, Integer durationMinutes, Double rating) {}
}
