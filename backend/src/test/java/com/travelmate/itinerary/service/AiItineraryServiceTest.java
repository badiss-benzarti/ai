package com.travelmate.itinerary.service;

import com.travelmate.common.ai.OllamaClient;
import com.travelmate.common.ai.OllamaException;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.service.AiItineraryService.AiDay;
import com.travelmate.itinerary.service.AiItineraryService.AiItem;
import com.travelmate.itinerary.service.AiItineraryService.AiPlan;
import com.travelmate.itinerary.service.ItineraryPlan.Slot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The AI answer is never trusted: these tests feed bad answers through a fake Ollama. */
class AiItineraryServiceTest {

    // 3 days, 2 nights, 600 EUR -> hotel max 135/night, activity max 82.5, meal max 41.25
    private static final TripInfo TRIP = new TripInfo(1L, 1L, "Rome",
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 600, List.of("history", "food"));

    private static final List<CatalogItem> HOTELS = List.of(
            hotel(301, "Cheap Hostel", 40, 3.5),
            hotel(302, "Nice Hotel", 120, 4.5),
            hotel(303, "Mid Hotel", 90, 4.0),
            hotel(304, "Luxury Palace", 400, 4.9));

    private static final List<CatalogItem> ACTIVITIES = List.of(
            activity(101, "Colosseum tour", "history", 55, 120, 4.8),
            activity(102, "Pantheon visit", "history", 5, 60, 4.6),
            activity(103, "Food tour", "food", 70, 180, 4.7),
            activity(104, "Vespa tour", "adventure", 150, 180, 4.5),
            activity(105, "Free walk", "sightseeing", 0, 90, 4.4),
            activity(106, "Art museum", "art", 20, 120, 4.3),
            activity(107, "Catacombs", "history", 10, 90, 4.2));

    private static final List<CatalogItem> RESTAURANTS = List.of(
            restaurant(201, "Pizza place", 12, 4.6),
            restaurant(202, "Trattoria", 30, 4.5),
            restaurant(203, "Market stalls", 14, 4.4),
            restaurant(204, "Michelin star", 250, 4.9),
            restaurant(205, "Street food", 8, 4.3),
            restaurant(206, "Osteria", 35, 4.2),
            restaurant(207, "Cafe", 10, 4.1));

    private OllamaClient ollama;
    private AiItineraryService service;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        service = new AiItineraryService(ollama);
    }

    @Test
    void sendsOnlyAffordableItemsToTheModel() {
        answer(new AiPlan(302L, List.of()));

        service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ollama).chatJson(anyString(), prompt.capture(), anyMap(), eq(AiPlan.class));
        assertThat(prompt.getValue())
                .contains("hotel at most 135 EUR per night", "Nice Hotel", "Colosseum tour", "Trattoria")
                .doesNotContain("Luxury Palace", "Vespa tour", "Michelin star");
    }

    @Test
    void dropsInventedRepeatedAndUnaffordableIds() {
        answer(new AiPlan(302L, List.of(
                day(1, act(101, "09:00", "11:00"), act(999, "12:00", "13:00"), act(104, "14:00", "17:00"),
                        meal(204, "19:30")),
                day(2, act(101, "09:00", "11:00"), act(102, "11:30", "12:30")))));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        assertThat(names(plan, 1)).contains("Colosseum tour")
                .doesNotContain("Vespa tour", "Michelin star");
        // The Colosseum is already on day 1, so day 2 keeps only the Pantheon
        assertThat(names(plan, 2)).contains("Pantheon visit").doesNotContain("Colosseum tour");
        assertThat(plan.aiUsed()).isTrue();
    }

    @Test
    void dropsItemsWithUnreadableTimesAndComputesMissingEndTimes() {
        answer(new AiPlan(302L, List.of(day(1,
                act(101, "morning", "11:00"),
                act(102, "9:30", "not a time")))));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        List<Slot> activities = activities(plan, 1);
        assertThat(activities).extracting(s -> s.item().name()).containsExactly("Pantheon visit");
        // End time taken from the catalog duration (60 min)
        assertThat(activities.get(0).start()).isEqualTo(LocalTime.of(9, 30));
        assertThat(activities.get(0).end()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void fillsDaysMissingFromTheAnswerWithTheRulePlan() {
        answer(new AiPlan(302L, List.of(day(1, act(101, "09:00", "11:00")))));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        assertThat(plan.days()).extracting(ItineraryPlan.Day::dayNumber).containsExactly(1, 2, 3);
        assertThat(plan.days()).allSatisfy(d -> assertThat(d.slots()).isNotEmpty());
        assertThat(allActivityIds(plan)).doesNotHaveDuplicates();
    }

    @Test
    void addsLunchAndDinnerWhenTheModelForgetsThem() {
        answer(new AiPlan(302L, List.of(day(1, act(101, "09:00", "11:00")))));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        assertThat(plan.days().get(0).slots())
                .filteredOn(s -> s.item().type() == ItemType.RESTAURANT)
                .extracting(Slot::start)
                .containsExactly(LocalTime.of(12, 30), LocalTime.of(19, 30));
    }

    @Test
    void keepsTheModelMealsWhenPresent() {
        answer(new AiPlan(302L, List.of(day(1,
                act(101, "09:00", "11:00"), meal(202, "13:00"), meal(201, "20:00")))));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        assertThat(plan.days().get(0).slots())
                .filteredOn(s -> s.item().type() == ItemType.RESTAURANT)
                .extracting(s -> s.item().name())
                .containsExactly("Trattoria", "Pizza place");
    }

    @Test
    void pushesBackOverlappingItems() {
        answer(new AiPlan(302L, List.of(day(1,
                act(101, "09:00", "11:00"),
                act(102, "10:00", "11:00"),
                meal(202, "12:30"), meal(201, "19:30")))));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        List<Slot> slots = plan.days().get(0).slots();
        assertThat(slots.get(1).item().name()).isEqualTo("Pantheon visit");
        assertThat(slots.get(1).start()).isEqualTo(LocalTime.of(11, 0));
        assertThat(slots.get(1).end()).isEqualTo(LocalTime.of(12, 0));
        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).start()).isAfterOrEqualTo(slots.get(i - 1).end());
        }
    }

    @Test
    void picksTheBestAffordableHotelWhenTheModelGivesAWrongHotelId() {
        answer(new AiPlan(999L, List.of(day(1, act(101, "09:00", "11:00")))));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        assertThat(plan.hotel().name()).isEqualTo("Nice Hotel");
    }

    @Test
    void usesTheRulePlanWhenOllamaIsDown() {
        when(ollama.chatJson(anyString(), anyString(), anyMap(), eq(AiPlan.class)))
                .thenThrow(new OllamaException("Connection refused", null));

        ItineraryPlan plan = service.plan(TRIP, ACTIVITIES, RESTAURANTS, HOTELS);

        assertThat(plan.aiUsed()).isFalse();
        assertThat(plan.hotel()).isNotNull();
        assertThat(plan.days()).hasSize(3).allSatisfy(d -> assertThat(d.slots()).isNotEmpty());
        assertThat(allActivityIds(plan)).doesNotHaveDuplicates();
    }

    @Test
    void refusesToPlanWithAnEmptyCatalog() {
        assertThatThrownBy(() -> service.plan(TRIP, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- Helpers ----------

    private void answer(AiPlan plan) {
        when(ollama.chatJson(anyString(), anyString(), anyMap(), eq(AiPlan.class))).thenReturn(plan);
    }

    private static AiDay day(int n, AiItem... items) {
        return new AiDay(n, "Day " + n, List.of(items));
    }

    private static AiItem act(long id, String start, String end) {
        return new AiItem("ACTIVITY", id, start, end, "fits");
    }

    private static AiItem meal(long id, String start) {
        return new AiItem("RESTAURANT", id, start, null, "tasty");
    }

    private static List<String> names(ItineraryPlan plan, int dayNumber) {
        return plan.days().get(dayNumber - 1).slots().stream().map(s -> s.item().name()).toList();
    }

    private static List<Slot> activities(ItineraryPlan plan, int dayNumber) {
        return plan.days().get(dayNumber - 1).slots().stream()
                .filter(s -> s.item().type() == ItemType.ACTIVITY).toList();
    }

    private static List<Long> allActivityIds(ItineraryPlan plan) {
        return plan.days().stream().flatMap(d -> d.slots().stream())
                .filter(s -> s.item().type() == ItemType.ACTIVITY).map(s -> s.item().id()).toList();
    }

    private static CatalogItem hotel(long id, String name, double price, double rating) {
        return new CatalogItem(id, ItemType.HOTEL, name, "hotel", price, null, rating);
    }

    private static CatalogItem activity(long id, String name, String category, double price, int minutes, double rating) {
        return new CatalogItem(id, ItemType.ACTIVITY, name, category, price, minutes, rating);
    }

    private static CatalogItem restaurant(long id, String name, double price, double rating) {
        return new CatalogItem(id, ItemType.RESTAURANT, name, "restaurant", price, 60, rating);
    }
}
