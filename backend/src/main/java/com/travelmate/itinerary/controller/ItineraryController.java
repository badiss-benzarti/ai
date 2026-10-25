package com.travelmate.itinerary.controller;

import com.travelmate.itinerary.dto.GenerateRequest;
import com.travelmate.itinerary.dto.GenerationResultDto;
import com.travelmate.itinerary.dto.ItemRequest;
import com.travelmate.itinerary.dto.ItineraryDto;
import com.travelmate.itinerary.dto.OptimizationResultDto;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.service.BudgetOptimizerService;
import com.travelmate.itinerary.service.ItineraryService;
import com.travelmate.itinerary.service.TripPlanningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    /** Until Module 1 (JWT) is integrated, every request belongs to user 1. */
    private static final String DEFAULT_USER = "1";

    private final ItineraryService itineraryService;
    private final BudgetOptimizerService optimizerService;
    private final TripPlanningService planningService;

    /** Trips of the user (from Module 2) to choose from in the UI. */
    @GetMapping("/trips")
    public List<TripInfo> trips(@RequestParam(defaultValue = DEFAULT_USER) Long userId) {
        return itineraryService.getTripsForUser(userId);
    }

    @GetMapping
    public List<ItineraryDto> list(@RequestParam(defaultValue = DEFAULT_USER) Long userId) {
        return itineraryService.getForUser(userId);
    }

    /**
     * AI: generates (or regenerates) the day-by-day itinerary of a trip,
     * then automatically replaces items with cheaper ones if it is over budget.
     */
    @PostMapping("/generate")
    public GenerationResultDto generate(@Valid @RequestBody GenerateRequest request) {
        return planningService.plan(request.tripId());
    }

    @GetMapping("/{id}")
    public ItineraryDto get(@PathVariable Long id) {
        return itineraryService.getById(id);
    }

    @GetMapping("/trip/{tripId}")
    public ItineraryDto getByTrip(@PathVariable Long tripId) {
        return itineraryService.getByTrip(tripId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        itineraryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Activities, restaurants or hotels of the destination, to add an item manually. */
    @GetMapping("/{id}/catalog")
    public List<CatalogItem> catalog(@PathVariable Long id, @RequestParam ItemType type) {
        return itineraryService.getCatalog(id, type);
    }

    @PostMapping("/{id}/days/{dayId}/items")
    public ItineraryDto addItem(@PathVariable Long id, @PathVariable Long dayId, @Valid @RequestBody ItemRequest request) {
        return itineraryService.addItem(id, dayId, request);
    }

    @PutMapping("/items/{itemId}")
    public ItineraryDto updateItem(@PathVariable Long itemId, @Valid @RequestBody ItemRequest request) {
        return itineraryService.updateItem(itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public ItineraryDto deleteItem(@PathVariable Long itemId) {
        return itineraryService.deleteItem(itemId);
    }

    /** AI: replaces items with cheaper alternatives when the itinerary is over budget. */
    @PostMapping("/{id}/optimize")
    public OptimizationResultDto optimize(@PathVariable Long id) {
        return optimizerService.optimize(id);
    }
}
