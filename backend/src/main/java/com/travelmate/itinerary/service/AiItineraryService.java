package com.travelmate.itinerary.service;

import com.travelmate.common.ai.OllamaClient;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.service.ItineraryPlan.Day;
import com.travelmate.itinerary.service.ItineraryPlan.Slot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI task of Module 6: generate a personalised day-by-day itinerary with Ollama.
 *
 * The model only chooses ids from the catalog we send it. Java then validates every id,
 * takes prices from the catalog and fills any gap with a rule-based plan,
 * so the result is always usable even if Ollama is down.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiItineraryService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm[:ss]");

    private static final String SYSTEM_PROMPT = """
            You are the travel planner of the TravelMate app. Build a realistic day-by-day itinerary.
            Rules:
            - Use ONLY items from the provided lists, referenced by their id. Never invent places.
            - Choose exactly one hotel (hotelId) for the whole stay.
            - Each day: 1 to 3 activities, plus lunch and dinner as RESTAURANT items.
            - Never repeat an activity. Avoid repeating restaurants.
            - Prefer items matching the traveller's interests.
            - Respect the spending limits: keep each day's activities + meals around the daily amount.
              Mix free or cheap activities with paid ones.
            - Times use 24h HH:mm format, in chronological order, without overlaps.
            - Give each day a short title (max 6 words).
            - For each item, the note explains in max 12 words why it fits this traveller's interests.
              Do not repeat the item name in the note.
            Answer only with JSON.
            """;

    private final OllamaClient ollama;

    public ItineraryPlan plan(TripInfo trip, List<CatalogItem> activities,
                              List<CatalogItem> restaurants, List<CatalogItem> hotels) {
        if (hotels.isEmpty() && activities.isEmpty() && restaurants.isEmpty()) {
            throw new IllegalStateException("No activities, restaurants or hotels found for " + trip.destination());
        }
        try {
            // A small model is bad at adding prices: only show it what the budget allows
            Limits limits = limits(trip);
            List<CatalogItem> aiHotels = affordable(hotels, limits.hotelPerNight(), 3);
            List<CatalogItem> aiActivities = affordable(activities, limits.activity(), 2 * trip.numberOfDays());
            List<CatalogItem> aiRestaurants = affordable(restaurants, limits.meal(), 6);
            AiPlan aiPlan = ollama.chatJson(SYSTEM_PROMPT,
                    buildPrompt(trip, limits, aiActivities, aiRestaurants, aiHotels), planSchema(), AiPlan.class);
            return validate(aiPlan, trip, aiActivities, aiRestaurants, aiHotels);
        } catch (Exception e) {
            log.warn("AI itinerary generation failed, using rule-based plan: {}", e.getMessage());
            return fallbackPlan(trip, activities, restaurants, hotels);
        }
    }

    // ---------- Prompt ----------

    /**
     * Budget split used to filter the catalog: up to 45% for the hotel (as {@link #pickHotel}),
     * the rest shared between the days, half for meals and half for activities.
     */
    private Limits limits(TripInfo trip) {
        double hotelPerNight = trip.budget() * 0.45 / trip.numberOfNights();
        double perDay = (trip.budget() - hotelPerNight * trip.numberOfNights()) / trip.numberOfDays();
        // A bit of slack per item: not every day has an expensive meal or activity
        return new Limits(hotelPerNight, perDay, perDay * 0.75, perDay * 0.5 / 2 * 1.5);
    }

    /** Items within {@code max}, or the {@code minCount} cheapest ones if too few fit. */
    private List<CatalogItem> affordable(List<CatalogItem> items, double max, int minCount) {
        List<CatalogItem> fitting = items.stream().filter(c -> c.price() <= max).toList();
        if (fitting.size() >= Math.min(minCount, items.size())) return fitting;
        return items.stream().sorted(Comparator.comparingDouble(CatalogItem::price)).limit(minCount).toList();
    }

    private String buildPrompt(TripInfo trip, Limits limits, List<CatalogItem> activities,
                               List<CatalogItem> restaurants, List<CatalogItem> hotels) {
        StringBuilder sb = new StringBuilder();
        sb.append("Destination: ").append(trip.destination()).append('\n')
          .append("Dates: ").append(trip.startDate()).append(" to ").append(trip.endDate())
          .append(" (").append(trip.numberOfDays()).append(" days, ").append(trip.numberOfNights()).append(" nights)\n")
          .append("Total budget: ").append(trip.budget()).append(" EUR\n")
          .append(String.format("Spending limits: hotel at most %.0f EUR per night, ", limits.hotelPerNight()))
          .append(String.format("activities + meals about %.0f EUR per day in total%n", limits.perDay()))
          .append("Interests: ").append(String.join(", ", trip.interests())).append("\n\n");
        appendList(sb, "HOTELS (price per night)", hotels);
        appendList(sb, "ACTIVITIES (price per person)", activities);
        appendList(sb, "RESTAURANTS (average price per meal)", restaurants);
        sb.append("Return the hotelId and the days, dayNumber from 1 to ").append(trip.numberOfDays()).append('.');
        return sb.toString();
    }

    private void appendList(StringBuilder sb, String title, List<CatalogItem> items) {
        sb.append(title).append(":\n");
        for (CatalogItem c : items) {
            sb.append("- id=").append(c.id()).append(" | ").append(c.name()).append(" | ").append(c.category())
              .append(" | ").append(c.price()).append(" EUR");
            if (c.durationMinutes() != null) sb.append(" | ").append(c.durationMinutes()).append(" min");
            if (c.rating() != null) sb.append(" | rating ").append(c.rating());
            sb.append('\n');
        }
        sb.append('\n');
    }

    private Map<String, Object> planSchema() {
        Map<String, Object> item = Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of("type", "string", "enum", List.of("ACTIVITY", "RESTAURANT")),
                        "refId", Map.of("type", "integer"),
                        "startTime", Map.of("type", "string"),
                        "endTime", Map.of("type", "string"),
                        "note", Map.of("type", "string")),
                "required", List.of("type", "refId", "startTime", "endTime", "note"));
        Map<String, Object> day = Map.of(
                "type", "object",
                "properties", Map.of(
                        "dayNumber", Map.of("type", "integer"),
                        "title", Map.of("type", "string"),
                        "items", Map.of("type", "array", "items", item)),
                "required", List.of("dayNumber", "title", "items"));
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "hotelId", Map.of("type", "integer"),
                        "days", Map.of("type", "array", "items", day)),
                "required", List.of("hotelId", "days"));
    }

    // ---------- Validation of the AI answer ----------

    private ItineraryPlan validate(AiPlan aiPlan, TripInfo trip, List<CatalogItem> activities,
                                   List<CatalogItem> restaurants, List<CatalogItem> hotels) {
        Map<Long, CatalogItem> activityById = byId(activities);
        Map<Long, CatalogItem> restaurantById = byId(restaurants);

        CatalogItem hotel = aiPlan.hotelId() == null ? null : byId(hotels).get(aiPlan.hotelId());
        if (hotel == null) hotel = pickHotel(trip, hotels);

        Map<Integer, AiDay> aiDays = aiPlan.days() == null ? Map.of() : aiPlan.days().stream()
                .filter(d -> d.dayNumber() != null)
                .collect(Collectors.toMap(AiDay::dayNumber, d -> d, (a, b) -> a));

        Set<Long> usedActivities = new HashSet<>();
        List<Day> days = new ArrayList<>();
        int aiDaysKept = 0;
        for (int n = 1; n <= trip.numberOfDays(); n++) {
            AiDay aiDay = aiDays.get(n);
            List<Slot> slots = new ArrayList<>();
            Set<Long> restaurantsToday = new HashSet<>();
            if (aiDay != null && aiDay.items() != null) {
                for (AiItem ai : aiDay.items()) {
                    CatalogItem c = null;
                    if ("ACTIVITY".equals(ai.type()) && ai.refId() != null && usedActivities.add(ai.refId())) {
                        c = activityById.get(ai.refId());
                    } else if ("RESTAURANT".equals(ai.type()) && ai.refId() != null && restaurantsToday.add(ai.refId())) {
                        c = restaurantById.get(ai.refId());
                    }
                    LocalTime start = parseTime(ai.startTime());
                    if (c == null || start == null) continue;
                    LocalTime end = parseTime(ai.endTime());
                    if (end == null || !end.isAfter(start)) end = start.plusMinutes(duration(c));
                    slots.add(new Slot(c, start, end, ai.note()));
                }
            }
            if (slots.isEmpty()) {
                days.add(fallbackDay(n, trip, activities, restaurants, usedActivities, hotel));
            } else {
                // The model often forgets a meal: complete the day with an affordable restaurant
                List<CatalogItem> meals = affordableRestaurants(trip, restaurants, hotel);
                addMissingMeal(slots, LocalTime.of(11, 0), LocalTime.of(16, 30), LocalTime.of(12, 30), meals, restaurantsToday, n);
                addMissingMeal(slots, LocalTime.of(16, 30), LocalTime.of(23, 0), LocalTime.of(19, 30), meals, restaurantsToday, n);
                slots = removeOverlaps(slots);
                String title = aiDay.title() == null || aiDay.title().isBlank() ? "Day " + n : aiDay.title();
                days.add(new Day(n, title, slots));
                aiDaysKept++;
            }
        }
        log.info("AI itinerary: {}/{} days kept from Ollama", aiDaysKept, trip.numberOfDays());
        return new ItineraryPlan(hotel, days, aiDaysKept > 0);
    }

    /** Adds a restaurant at {@code at} when no meal starts between {@code from} and {@code to}. */
    private void addMissingMeal(List<Slot> slots, LocalTime from, LocalTime to, LocalTime at,
                                List<CatalogItem> meals, Set<Long> restaurantsToday, int dayNumber) {
        boolean hasMeal = slots.stream().anyMatch(s -> s.item().type() == ItemType.RESTAURANT
                && !s.start().isBefore(from) && s.start().isBefore(to));
        if (hasMeal || meals.isEmpty()) return;
        // Rotate through the list so the added meals differ from one day to the next
        for (int i = 0; i < meals.size(); i++) {
            CatalogItem meal = meals.get((dayNumber + i) % meals.size());
            if (restaurantsToday.add(meal.id())) {
                slots.add(slot(meal, at));
                return;
            }
        }
    }

    /** Sorts the day and pushes back any item that starts before the previous one ends. */
    private List<Slot> removeOverlaps(List<Slot> slots) {
        List<Slot> sorted = new ArrayList<>(slots);
        sorted.sort(Comparator.comparing(Slot::start));
        List<Slot> result = new ArrayList<>();
        LocalTime previousEnd = LocalTime.MIN;
        for (Slot s : sorted) {
            LocalTime start = s.start().isBefore(previousEnd) ? previousEnd : s.start();
            LocalTime end = start.plusMinutes(Duration.between(s.start(), s.end()).toMinutes());
            if (end.isBefore(start)) break;   // would go past midnight: drop the rest of the day
            result.add(new Slot(s.item(), start, end, s.note()));
            previousEnd = end;
        }
        return result;
    }

    private LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalTime.parse(s.trim(), TIME);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Rule-based fallback ----------

    ItineraryPlan fallbackPlan(TripInfo trip, List<CatalogItem> activities,
                               List<CatalogItem> restaurants, List<CatalogItem> hotels) {
        CatalogItem hotel = pickHotel(trip, hotels);
        Set<Long> used = new HashSet<>();
        List<Day> days = new ArrayList<>();
        for (int n = 1; n <= trip.numberOfDays(); n++) {
            days.add(fallbackDay(n, trip, activities, restaurants, used, hotel));
        }
        return new ItineraryPlan(hotel, days, false);
    }

    /** Morning activity, lunch, afternoon activity, dinner. */
    private Day fallbackDay(int n, TripInfo trip, List<CatalogItem> activities, List<CatalogItem> restaurants,
                            Set<Long> usedActivities, CatalogItem hotel) {
        List<CatalogItem> freeActivities = activities.stream()
                .filter(a -> !usedActivities.contains(a.id()))
                .sorted(Comparator.comparingDouble((CatalogItem a) -> -score(a, trip)))
                .limit(2)
                .toList();
        freeActivities.forEach(a -> usedActivities.add(a.id()));

        List<CatalogItem> meals = affordableRestaurants(trip, restaurants, hotel);
        List<Slot> slots = new ArrayList<>();
        if (!freeActivities.isEmpty()) slots.add(slot(freeActivities.get(0), LocalTime.of(9, 0)));
        if (!meals.isEmpty()) slots.add(slot(meals.get((2 * n) % meals.size()), LocalTime.of(12, 30)));
        if (freeActivities.size() > 1) slots.add(slot(freeActivities.get(1), LocalTime.of(14, 30)));
        if (!meals.isEmpty()) slots.add(slot(meals.get((2 * n + 1) % meals.size()), LocalTime.of(19, 30)));

        String title = freeActivities.isEmpty() ? "Free day in " + trip.destination()
                : freeActivities.stream().map(a -> capitalize(a.category())).distinct().collect(Collectors.joining(" & "));
        return new Day(n, title, slots);
    }

    /** Best-rated hotel that costs at most 45% of the budget, otherwise the cheapest one. */
    CatalogItem pickHotel(TripInfo trip, List<CatalogItem> hotels) {
        if (hotels.isEmpty()) return null;
        double maxPerNight = trip.budget() * 0.45 / trip.numberOfNights();
        return hotels.stream()
                .filter(h -> h.price() <= maxPerNight)
                .max(Comparator.comparingDouble(h -> h.rating() == null ? 0 : h.rating()))
                .orElseGet(() -> hotels.stream().min(Comparator.comparingDouble(CatalogItem::price)).get());
    }

    /** Restaurants that fit about 35% of the budget left after the hotel, best rated first. */
    private List<CatalogItem> affordableRestaurants(TripInfo trip, List<CatalogItem> restaurants, CatalogItem hotel) {
        double hotelCost = hotel == null ? 0 : hotel.price() * trip.numberOfNights();
        double maxPerMeal = Math.max(0, trip.budget() - hotelCost) * 0.35 / (2.0 * trip.numberOfDays());
        List<CatalogItem> fitting = restaurants.stream()
                .filter(r -> r.price() <= maxPerMeal)
                .sorted(Comparator.comparingDouble((CatalogItem r) -> r.rating() == null ? 0 : -r.rating()))
                .toList();
        if (!fitting.isEmpty()) return fitting;
        return restaurants.stream().sorted(Comparator.comparingDouble(CatalogItem::price)).limit(2).toList();
    }

    private double score(CatalogItem a, TripInfo trip) {
        boolean matches = trip.interests().stream().anyMatch(i -> i.equalsIgnoreCase(a.category()));
        return (matches ? 2 : 0) + (a.rating() == null ? 0 : a.rating());
    }

    private Slot slot(CatalogItem c, LocalTime start) {
        return new Slot(c, start, start.plusMinutes(duration(c)), null);
    }

    private int duration(CatalogItem c) {
        if (c.durationMinutes() != null) return c.durationMinutes();
        return c.type() == ItemType.RESTAURANT ? 60 : 120;
    }

    private static Map<Long, CatalogItem> byId(List<CatalogItem> items) {
        return items.stream().collect(Collectors.toMap(CatalogItem::id, c -> c, (a, b) -> a));
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- JSON shape returned by Ollama ----------

    /** Maximum prices (EUR) sent to the model. */
    private record Limits(double hotelPerNight, double perDay, double activity, double meal) {}

    record AiPlan(Long hotelId, List<AiDay> days) {}

    record AiDay(Integer dayNumber, String title, List<AiItem> items) {}

    record AiItem(String type, Long refId, String startTime, String endTime, String note) {}
}
