package com.travelmate.itinerary.service;

import com.travelmate.common.ai.OllamaClient;
import com.travelmate.itinerary.dto.ItineraryDto;
import com.travelmate.itinerary.dto.OptimizationResultDto;
import com.travelmate.itinerary.dto.ReplacementDto;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.entity.Itinerary;
import com.travelmate.itinerary.entity.ItineraryItem;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.CatalogProvider;
import com.travelmate.itinerary.integration.TripInfoProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * AI task of Module 6: when the itinerary is over budget, replace items with cheaper alternatives.
 *
 * 1. Java lists, for each item, the cheaper alternatives from the catalog.
 * 2. Ollama picks which items to swap (keeping the traveller's interests) and explains why.
 * 3. Java applies only valid swaps and recomputes the total (up to 3 rounds).
 * 4. If still over budget (or Ollama is down), a greedy algorithm finishes the job.
 * 5. Last resort: remove paid activities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetOptimizerService {

    private static final int MAX_AI_ROUNDS = 3;
    private static final int MAX_ALTERNATIVES = 4;
    private static final List<ItemType> REPLACEABLE = List.of(ItemType.HOTEL, ItemType.ACTIVITY, ItemType.RESTAURANT);

    private static final String SYSTEM_PROMPT = """
            You are the budget optimizer of the TravelMate app. The itinerary is over budget.
            Replace some items with cheaper alternatives so the total fits the budget,
            while keeping the trip enjoyable and matching the traveller's interests.
            Rules:
            - Only use the itemId values and alternative ids given in the list.
            - Make as few replacements as possible, prefer alternatives of the same category.
            - For each replacement give a short reason (max 15 words).
            Answer only with JSON.
            """;

    private final ItineraryService itineraryService;
    private final CatalogProvider catalogProvider;
    private final TripInfoProvider tripProvider;
    private final OllamaClient ollama;

    @Transactional
    public OptimizationResultDto optimize(Long itineraryId) {
        Itinerary it = itineraryService.findItinerary(itineraryId);
        it.recalculateTotal();
        double before = it.getTotalEstimatedCost();
        if (before <= it.getBudget()) {
            return result(it, before, List.of(), false, "The itinerary is already within budget.");
        }

        Map<ItemType, List<CatalogItem>> catalog = new EnumMap<>(ItemType.class);
        for (ItemType type : REPLACEABLE) {
            catalog.put(type, catalogProvider.getByType(type, it.getDestination()));
        }
        List<String> interests = interests(it);
        List<ReplacementDto> replacements = new ArrayList<>();
        boolean aiUsed = false;

        for (int round = 1; round <= MAX_AI_ROUNDS && overBudget(it); round++) {
            Map<ItineraryItem, List<CatalogItem>> alternatives = cheaperAlternatives(it, catalog, MAX_ALTERNATIVES);
            if (alternatives.isEmpty()) break;
            try {
                AiSwaps swaps = ollama.chatJson(SYSTEM_PROMPT, buildPrompt(it, interests, alternatives),
                        swapSchema(), AiSwaps.class);
                int applied = applyAiSwaps(it, swaps, alternatives, replacements);
                log.info("Budget optimizer round {}: {} AI replacement(s)", round, applied);
                if (applied == 0) break;
                aiUsed = true;
            } catch (Exception e) {
                log.warn("AI budget optimization failed, using greedy algorithm: {}", e.getMessage());
                break;
            }
        }

        while (overBudget(it) && greedyStep(it, catalog, replacements)) {
            // each step strictly lowers the total, so this always ends
        }
        while (overBudget(it) && removeActivityStep(it, replacements)) {
            // last resort when no cheaper alternative is left
        }

        double after = it.getTotalEstimatedCost();
        String message = overBudget(it)
                ? String.format("Saved %.0f EUR but still %.0f EUR over budget. Increase the budget or choose a cheaper hotel.", before - after, after - it.getBudget())
                : String.format("Saved %.0f EUR with %d change(s). The itinerary now fits the budget.",
                        before - after, replacements.size());
        return result(it, before, replacements, aiUsed, message);
    }

    // ---------- Alternatives ----------

    /**
     * For each catalog item of the itinerary: cheaper items of the same type.
     * Activities already planned are excluded; restaurants already planned come last, then same category first.
     */
    private Map<ItineraryItem, List<CatalogItem>> cheaperAlternatives(Itinerary it, Map<ItemType, List<CatalogItem>> catalog,
                                                                      int limit) {
        Set<Long> usedActivities = new HashSet<>();
        Set<Long> usedRestaurants = new HashSet<>();
        for (ItineraryItem i : it.allItems()) {
            if (i.getType() == ItemType.ACTIVITY) usedActivities.add(i.getRefId());
            if (i.getType() == ItemType.RESTAURANT) usedRestaurants.add(i.getRefId());
        }

        Map<ItineraryItem, List<CatalogItem>> result = new LinkedHashMap<>();
        for (ItineraryItem item : it.allItems()) {
            if (item.getRefId() == null || !catalog.containsKey(item.getType())) continue;
            List<CatalogItem> alts = catalog.get(item.getType()).stream()
                    .filter(c -> c.price() < item.getUnitPrice())
                    .filter(c -> item.getType() != ItemType.ACTIVITY || !usedActivities.contains(c.id()))
                    .sorted(Comparator
                            .comparing((CatalogItem c) -> usedRestaurants.contains(c.id()))
                            .thenComparing(c -> !Objects.equals(c.category(), item.getCategory()))
                            .thenComparing(c -> c.rating() == null ? 0 : -c.rating()))
                    .limit(limit)
                    .toList();
            if (!alts.isEmpty()) result.put(item, alts);
        }
        return result;
    }

    // ---------- AI ----------

    private String buildPrompt(Itinerary it, List<String> interests, Map<ItineraryItem, List<CatalogItem>> alternatives) {
        StringBuilder sb = new StringBuilder();
        sb.append("Destination: ").append(it.getDestination()).append('\n')
          .append("Budget: ").append(it.getBudget()).append(" EUR\n")
          .append("Current total: ").append(it.getTotalEstimatedCost()).append(" EUR\n")
          .append("Need to save at least: ").append(it.getTotalEstimatedCost() - it.getBudget()).append(" EUR\n")
          .append("Interests: ").append(String.join(", ", interests)).append("\n\n")
          .append("Items you may replace:\n");
        alternatives.forEach((item, alts) -> {
            sb.append("- itemId=").append(item.getId()).append(" | day ").append(item.getDay().getDayNumber())
              .append(" | ").append(item.getType()).append(" | ").append(item.getName())
              .append(" | ").append(item.getCategory()).append(" | ").append(item.getUnitPrice()).append(" EUR");
            if (item.getQuantity() > 1) {
                sb.append(" x ").append(item.getQuantity()).append(" = ").append(item.getEstimatedCost()).append(" EUR");
            }
            sb.append("\n  alternatives:");
            for (CatalogItem c : alts) {
                sb.append(" [id=").append(c.id()).append(' ').append(c.name()).append(", ").append(c.category())
                  .append(", ").append(c.price()).append(" EUR");
                if (c.rating() != null) sb.append(", rating ").append(c.rating());
                sb.append(']');
            }
            sb.append('\n');
        });
        return sb.toString();
    }

    private Map<String, Object> swapSchema() {
        Map<String, Object> swap = Map.of(
                "type", "object",
                "properties", Map.of(
                        "itemId", Map.of("type", "integer"),
                        "newRefId", Map.of("type", "integer"),
                        "reason", Map.of("type", "string")),
                "required", List.of("itemId", "newRefId", "reason"));
        return Map.of(
                "type", "object",
                "properties", Map.of("replacements", Map.of("type", "array", "items", swap)),
                "required", List.of("replacements"));
    }

    private int applyAiSwaps(Itinerary it, AiSwaps swaps, Map<ItineraryItem, List<CatalogItem>> alternatives,
                             List<ReplacementDto> replacements) {
        if (swaps == null || swaps.replacements() == null) return 0;
        Map<Long, ItineraryItem> itemsById = new HashMap<>();
        alternatives.keySet().forEach(i -> itemsById.put(i.getId(), i));
        Set<Long> usedNewActivities = new HashSet<>();
        int applied = 0;
        for (AiSwap swap : swaps.replacements()) {
            if (!overBudget(it)) break;   // the model often proposes more swaps than needed
            ItineraryItem item = itemsById.remove(swap.itemId());   // each item at most once per round
            if (item == null) continue;
            Optional<CatalogItem> alt = alternatives.get(item).stream()
                    .filter(c -> c.id().equals(swap.newRefId()))
                    .findFirst();
            if (alt.isEmpty()) continue;
            if (item.getType() == ItemType.ACTIVITY && !usedNewActivities.add(alt.get().id())) continue;
            replace(item, alt.get(), swap.reason(), replacements);
            it.recalculateTotal();
            applied++;
        }
        return applied;
    }

    // ---------- Greedy fallback ----------

    /**
     * If one swap is enough to fit the budget, take the one that saves the least (smallest downgrade).
     * Otherwise take the swap that saves the most. Returns false when nothing can be replaced.
     */
    private boolean greedyStep(Itinerary it, Map<ItemType, List<CatalogItem>> catalog, List<ReplacementDto> replacements) {
        double overspend = it.getTotalEstimatedCost() - it.getBudget();
        ItineraryItem bestItem = null;
        CatalogItem bestAlt = null;
        double bestSaving = 0;
        boolean bestCloses = false;

        for (Map.Entry<ItineraryItem, List<CatalogItem>> e : cheaperAlternatives(it, catalog, Integer.MAX_VALUE).entrySet()) {
            ItineraryItem item = e.getKey();
            for (CatalogItem alt : e.getValue()) {
                double saving = (item.getUnitPrice() - alt.price()) * item.getQuantity();
                boolean closes = saving >= overspend;
                boolean better = bestItem == null
                        || (closes && !bestCloses)
                        || (closes && saving < bestSaving)
                        || (!closes && !bestCloses && saving > bestSaving);
                if (better) {
                    bestItem = item;
                    bestAlt = alt;
                    bestSaving = saving;
                    bestCloses = closes;
                }
            }
        }
        if (bestItem == null) return false;
        replace(bestItem, bestAlt, String.format("Cheaper similar option, saves %.0f EUR", bestSaving), replacements);
        it.recalculateTotal();
        return true;
    }

    /** Removes the cheapest paid activity that closes the gap, otherwise the most expensive one. */
    private boolean removeActivityStep(Itinerary it, List<ReplacementDto> replacements) {
        double overspend = it.getTotalEstimatedCost() - it.getBudget();
        List<ItineraryItem> paid = it.allItems().stream()
                .filter(i -> i.getType() == ItemType.ACTIVITY && i.getEstimatedCost() > 0)
                .sorted(Comparator.comparingDouble(ItineraryItem::getEstimatedCost))
                .toList();
        if (paid.isEmpty()) return false;
        ItineraryItem victim = paid.stream().filter(i -> i.getEstimatedCost() >= overspend).findFirst()
                .orElse(paid.get(paid.size() - 1));
        replacements.add(new ReplacementDto(victim.getId(), victim.getDay().getDayNumber(), victim.getType(),
                victim.getName(), "(removed, free time)", victim.getEstimatedCost(), 0,
                "No cheaper alternative left, activity removed to fit the budget"));
        victim.getDay().getItems().remove(victim);
        it.recalculateTotal();
        return true;
    }

    // ---------- Helpers ----------

    private void replace(ItineraryItem item, CatalogItem alt, String reason, List<ReplacementDto> replacements) {
        double oldCost = item.getEstimatedCost();
        String oldName = item.getName();
        item.setRefId(alt.id());
        item.setName(alt.name());
        item.setCategory(alt.category());
        item.setPricing(alt.price(), item.getQuantity());
        if (item.getStartTime() != null && alt.durationMinutes() != null && item.getType() != ItemType.HOTEL) {
            item.setEndTime(item.getStartTime().plusMinutes(alt.durationMinutes()));
        }
        item.setNotes("Replaces " + oldName + ": " + reason);
        replacements.add(new ReplacementDto(item.getId(), item.getDay().getDayNumber(), item.getType(),
                oldName, alt.name(), oldCost, item.getEstimatedCost(), reason));
    }

    private boolean overBudget(Itinerary it) {
        return it.getTotalEstimatedCost() > it.getBudget();
    }

    private List<String> interests(Itinerary it) {
        try {
            return tripProvider.getTrip(it.getTripId()).interests();
        } catch (Exception e) {
            return List.of();
        }
    }

    private OptimizationResultDto result(Itinerary it, double before, List<ReplacementDto> replacements,
                                         boolean aiUsed, String message) {
        return new OptimizationResultDto(ItineraryDto.from(it), it.getBudget(), before, it.getTotalEstimatedCost(),
                !overBudget(it), aiUsed, replacements, message);
    }

    record AiSwaps(List<AiSwap> replacements) {}

    record AiSwap(Long itemId, Long newRefId, String reason) {}
}
