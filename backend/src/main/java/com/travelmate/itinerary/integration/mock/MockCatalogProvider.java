package com.travelmate.itinerary.integration.mock;

import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.CatalogProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MockCatalogProvider implements CatalogProvider {

    private final MockDataStore store;

    @Override
    public List<CatalogItem> getActivities(String destination) {
        return store.items(ItemType.ACTIVITY, destination);
    }

    @Override
    public List<CatalogItem> getRestaurants(String destination) {
        return store.items(ItemType.RESTAURANT, destination);
    }

    @Override
    public List<CatalogItem> getHotels(String destination) {
        return store.items(ItemType.HOTEL, destination);
    }
}
