package com.travelmate.itinerary.service;

import com.travelmate.common.ai.OllamaClient;
import com.travelmate.common.ai.OllamaException;
import com.travelmate.itinerary.dto.OptimizationResultDto;
import com.travelmate.itinerary.dto.ReplacementDto;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.entity.Itinerary;
import com.travelmate.itinerary.entity.ItineraryDay;
import com.travelmate.itinerary.entity.ItineraryItem;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.CatalogProvider;
import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.integration.TripInfoProvider;
import com.travelmate.itinerary.service.BudgetOptimizerService.AiSwap;
import com.travelmate.itinerary.service.BudgetOptimizerService.AiSwaps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Budget fixing with a fake Ollama and a small catalog. */
class BudgetOptimizerServiceTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 10);

    /** A small catalog for Rome, enough to test every path of the optimizer. */
    private static final CatalogProvider CATALOG = new CatalogProvider() {
        @Override
        public List<CatalogItem> getActivities(String destination) {
            return List.of(
                    new CatalogItem(101L, ItemType.ACTIVITY, "Guided tour", "history", 60, 120, 4.8),
                    new CatalogItem(102L, ItemType.ACTIVITY, "Self ticket", "history", 20, 90, 4.5),
                    new CatalogItem(103L, ItemType.ACTIVITY, "Free walk", "sightseeing", 0, 90, 4.4));
        }

        @Override
        public List<CatalogItem> getRestaurants(String destination) {
            return List.of(
                    new CatalogItem(201L, ItemType.RESTAURANT, "Fancy", "restaurant", 80, 90, 4.9),
                    new CatalogItem(202L, ItemType.RESTAURANT, "Pizza", "restaurant", 12, 45, 4.6),
                    new CatalogItem(203L, ItemType.RESTAURANT, "Trattoria", "restaurant", 25, 60, 4.5));
        }

        @Override
        public List<CatalogItem> getHotels(String destination) {
            return List.of(
                    new CatalogItem(301L, ItemType.HOTEL, "Luxury", "hotel", 200, null, 4.9),
                    new CatalogItem(302L, ItemType.HOTEL, "Mid", "hotel", 100, null, 4.5),
                    new CatalogItem(303L, ItemType.HOTEL, "Hostel", "hotel", 40, null, 4.0));
        }
    };

    private OllamaClient ollama;
    private ItineraryService itineraryService;
    private BudgetOptimizerService optimizer;
    private long nextItemId = 1;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        itineraryService = mock(ItineraryService.class);
        TripInfoProvider trips = mock(TripInfoProvider.class);
        when(trips.getTrip(anyLong())).thenReturn(new TripInfo(1L, 1L, "Rome", DAY_1, DAY_1.plusDays(2),
                300, List.of("history")));
        optimizer = new BudgetOptimizerService(itineraryService, CATALOG, trips, ollama);
    }

    @Test
    void doesNothingWhenAlreadyWithinBudget() {
        itinerary(1000, item(ItemType.HOTEL, 301, "Luxury", 200, 2));

        OptimizationResultDto result = optimizer.optimize(1L);

        assertThat(result.replacements()).isEmpty();
        assertThat(result.withinBudget()).isTrue();
        verifyNoInteractions(ollama);
    }

    @Test
    void stopsApplyingAiSwapsOnceTheBudgetFits() {
        // 400 + 60 + 80 = 540 for a 300 budget
        Itinerary it = itinerary(300,
                item(ItemType.HOTEL, 301, "Luxury", 200, 2),
                item(ItemType.ACTIVITY, 101, "Guided tour", 60, 1),
                item(ItemType.RESTAURANT, 201, "Fancy", 80, 1));
        // The model proposes 3 swaps but the first 2 are enough: 540 - 200 - 40 = 300
        aiAnswers(new AiSwaps(List.of(
                new AiSwap(1L, 302L, "Comfortable and cheaper"),
                new AiSwap(2L, 102L, "Same monument, no guide"),
                new AiSwap(3L, 202L, "Cheaper dinner"))));

        OptimizationResultDto result = optimizer.optimize(1L);

        assertThat(result.aiUsed()).isTrue();
        assertThat(result.withinBudget()).isTrue();
        assertThat(result.totalAfter()).isEqualTo(300);
        assertThat(result.replacements()).extracting(ReplacementDto::newName).containsExactly("Mid", "Self ticket");
        assertThat(it.allItems()).extracting(ItineraryItem::getName).contains("Fancy");
    }

    @Test
    void ignoresInvalidAiSwapsAndFinishesWithTheGreedyAlgorithm() {
        itinerary(300,
                item(ItemType.HOTEL, 301, "Luxury", 200, 2),
                item(ItemType.ACTIVITY, 101, "Guided tour", 60, 1),
                item(ItemType.RESTAURANT, 201, "Fancy", 80, 1));
        aiAnswers(new AiSwaps(List.of(
                new AiSwap(999L, 302L, "Unknown item"),
                new AiSwap(1L, 301L, "Not cheaper"),
                new AiSwap(2L, 777L, "Unknown alternative"))));

        OptimizationResultDto result = optimizer.optimize(1L);

        // Over by 240: the hostel (saves 320) is the only single swap that closes the gap
        assertThat(result.aiUsed()).isFalse();
        assertThat(result.withinBudget()).isTrue();
        assertThat(result.replacements()).extracting(ReplacementDto::newName).containsExactly("Hostel");
        assertThat(result.totalAfter()).isEqualTo(220);
    }

    @Test
    void usesTheGreedyAlgorithmWhenOllamaIsDown() {
        itinerary(300,
                item(ItemType.HOTEL, 301, "Luxury", 200, 2),
                item(ItemType.RESTAURANT, 201, "Fancy", 80, 1));
        when(ollama.chatJson(anyString(), anyString(), anyMap(), eq(AiSwaps.class)))
                .thenThrow(new OllamaException("Connection refused", null));

        OptimizationResultDto result = optimizer.optimize(1L);

        assertThat(result.aiUsed()).isFalse();
        assertThat(result.withinBudget()).isTrue();
        assertThat(result.totalAfter()).isLessThanOrEqualTo(300);
    }

    @Test
    void removesAPaidActivityWhenNothingCheaperIsLeft() {
        // The free walk is already planned, so the self ticket has no cheaper alternative
        Itinerary it = itinerary(50,
                item(ItemType.HOTEL, 303, "Hostel", 40, 1),
                item(ItemType.ACTIVITY, 103, "Free walk", 0, 1),
                item(ItemType.ACTIVITY, 102, "Self ticket", 20, 1));
        when(ollama.chatJson(anyString(), anyString(), anyMap(), eq(AiSwaps.class)))
                .thenThrow(new OllamaException("Connection refused", null));

        OptimizationResultDto result = optimizer.optimize(1L);

        assertThat(result.withinBudget()).isTrue();
        assertThat(result.replacements()).singleElement()
                .satisfies(r -> assertThat(r.newName()).isEqualTo("(removed, free time)"));
        assertThat(it.allItems()).extracting(ItineraryItem::getName).doesNotContain("Self ticket");
    }

    @Test
    void saysSoWhenTheBudgetCannotBeReached() {
        itinerary(10, item(ItemType.HOTEL, 303, "Hostel", 40, 1));

        OptimizationResultDto result = optimizer.optimize(1L);

        assertThat(result.withinBudget()).isFalse();
        assertThat(result.message()).contains("still 30 EUR over budget");
    }

    // ---------- Helpers ----------

    private void aiAnswers(AiSwaps swaps) {
        when(ollama.chatJson(anyString(), anyString(), anyMap(), eq(AiSwaps.class))).thenReturn(swaps);
    }

    /** One-day itinerary with the given items, returned by the mocked ItineraryService. */
    private Itinerary itinerary(double budget, ItineraryItem... items) {
        Itinerary it = new Itinerary();
        it.setId(1L);
        it.setTripId(1L);
        it.setUserId(1L);
        it.setDestination("Rome");
        it.setStartDate(DAY_1);
        it.setEndDate(DAY_1);
        it.setBudget(budget);
        ItineraryDay day = new ItineraryDay(1, DAY_1, "Day 1");
        it.addDay(day);
        for (ItineraryItem item : items) day.addItem(item);
        it.recalculateTotal();
        when(itineraryService.findItinerary(1L)).thenReturn(it);
        return it;
    }

    private ItineraryItem item(ItemType type, long refId, String name, double price, int quantity) {
        ItineraryItem item = new ItineraryItem();
        item.setId(nextItemId++);
        item.setType(type);
        item.setRefId(refId);
        item.setName(name);
        item.setCategory(switch (type) {
            case HOTEL -> "hotel";
            case RESTAURANT -> "restaurant";
            default -> refId == 103 ? "sightseeing" : "history";
        });
        item.setStartTime(LocalTime.of(8, 0).plusHours(nextItemId));
        item.setPricing(price, quantity);
        return item;
    }
}
