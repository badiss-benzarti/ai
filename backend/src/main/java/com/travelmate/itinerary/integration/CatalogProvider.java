package com.travelmate.itinerary.integration;

import com.travelmate.itinerary.entity.ItemType;

import java.util.List;
import java.util.Optional;

/**
 * Access to activities (Module 3), restaurants (Module 4) and hotels (Module 5).
 * INTEGRATION: replace MockCatalogProvider with an implementation that calls the other modules' services.
 */
public interface CatalogProvider {

    List<CatalogItem> getActivities(String destination);

    List<CatalogItem> getRestaurants(String destination);

    List<CatalogItem> getHotels(String destination);

    default List<CatalogItem> getByType(ItemType type, String destination) {
        return switch (type) {
            case ACTIVITY -> getActivities(destination);
            case RESTAURANT -> getRestaurants(destination);
            case HOTEL -> getHotels(destination);
            default -> List.of();
        };
    }

    default Optional<CatalogItem> find(ItemType type, Long id, String destination) {
        return getByType(type, destination).stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
