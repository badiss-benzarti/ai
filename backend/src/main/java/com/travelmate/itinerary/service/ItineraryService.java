package com.travelmate.itinerary.service;

import com.travelmate.itinerary.dto.ItemRequest;
import com.travelmate.itinerary.dto.ItineraryDto;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.entity.Itinerary;
import com.travelmate.itinerary.entity.ItineraryDay;
import com.travelmate.itinerary.entity.ItineraryItem;
import com.travelmate.itinerary.integration.CatalogItem;
import com.travelmate.itinerary.integration.CatalogProvider;
import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.integration.TripInfoProvider;
import com.travelmate.itinerary.repository.ItineraryDayRepository;
import com.travelmate.itinerary.repository.ItineraryItemRepository;
import com.travelmate.itinerary.repository.ItineraryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ItineraryService {

    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final TripInfoProvider tripProvider;
    private final CatalogProvider catalogProvider;
    private final AiItineraryService aiItineraryService;

    public List<TripInfo> getTripsForUser(Long userId) {
        return tripProvider.getTripsForUser(userId);
    }

    public List<CatalogItem> getCatalog(Long itineraryId, ItemType type) {
        return catalogProvider.getByType(type, findItinerary(itineraryId).getDestination());
    }

    /**
     * Generates (or regenerates) the itinerary of a trip.
     * Not transactional on purpose: the Ollama call can take a minute.
     */
    public ItineraryDto generate(Long tripId) {
        TripInfo trip = tripProvider.getTrip(tripId);
        ItineraryPlan plan = aiItineraryService.plan(trip,
                catalogProvider.getActivities(trip.destination()),
                catalogProvider.getRestaurants(trip.destination()),
                catalogProvider.getHotels(trip.destination()));

        itineraryRepository.deleteAll(itineraryRepository.findByTripId(tripId));
        Itinerary saved = itineraryRepository.save(toEntity(trip, plan));
        return ItineraryDto.from(saved);
    }

    private Itinerary toEntity(TripInfo trip, ItineraryPlan plan) {
        Itinerary it = new Itinerary();
        it.setTripId(trip.id());
        it.setUserId(trip.userId());
        it.setDestination(trip.destination());
        it.setStartDate(trip.startDate());
        it.setEndDate(trip.endDate());
        it.setBudget(trip.budget());
        it.setGeneratedByAi(plan.aiUsed());

        for (ItineraryPlan.Day planDay : plan.days()) {
            ItineraryDay day = new ItineraryDay(planDay.dayNumber(),
                    trip.startDate().plusDays(planDay.dayNumber() - 1L), planDay.title());
            if (planDay.dayNumber() == 1 && plan.hotel() != null) {
                ItineraryItem hotel = fromCatalog(plan.hotel(), LocalTime.of(8, 0), null, trip.numberOfNights());
                hotel.setNotes("Check-in, " + trip.numberOfNights() + " night(s)");
                day.addItem(hotel);
            }
            for (ItineraryPlan.Slot slot : planDay.slots()) {
                ItineraryItem item = fromCatalog(slot.item(), slot.start(), slot.end(), 1);
                item.setNotes(slot.note());
                day.addItem(item);
            }
            it.addDay(day);
        }
        it.recalculateTotal();
        return it;
    }

    private ItineraryItem fromCatalog(CatalogItem c, LocalTime start, LocalTime end, int quantity) {
        ItineraryItem item = new ItineraryItem();
        item.setType(c.type());
        item.setRefId(c.id());
        item.setName(c.name());
        item.setCategory(c.category());
        item.setStartTime(start);
        item.setEndTime(end);
        item.setPricing(c.price(), quantity);
        return item;
    }

    @Transactional(readOnly = true)
    public ItineraryDto getById(Long id) {
        return ItineraryDto.from(findItinerary(id));
    }

    @Transactional(readOnly = true)
    public ItineraryDto getByTrip(Long tripId) {
        return itineraryRepository.findByTripId(tripId).stream().findFirst()
                .map(ItineraryDto::from)
                .orElseThrow(() -> new EntityNotFoundException("No itinerary for trip " + tripId));
    }

    @Transactional(readOnly = true)
    public List<ItineraryDto> getForUser(Long userId) {
        return itineraryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(ItineraryDto::from).toList();
    }

    @Transactional
    public void delete(Long id) {
        itineraryRepository.delete(findItinerary(id));
    }

    @Transactional
    public ItineraryDto addItem(Long itineraryId, Long dayId, ItemRequest request) {
        ItineraryDay day = dayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day " + dayId + " not found in itinerary " + itineraryId));
        ItineraryItem item = new ItineraryItem();
        apply(item, request, day.getItinerary().getDestination());
        day.addItem(item);
        return saveWithTotal(day.getItinerary());
    }

    @Transactional
    public ItineraryDto updateItem(Long itemId, ItemRequest request) {
        ItineraryItem item = findItem(itemId);
        Itinerary it = item.getDay().getItinerary();
        apply(item, request, it.getDestination());
        return saveWithTotal(it);
    }

    @Transactional
    public ItineraryDto deleteItem(Long itemId) {
        ItineraryItem item = findItem(itemId);
        ItineraryDay day = item.getDay();
        day.getItems().remove(item);
        return saveWithTotal(day.getItinerary());
    }

    /** Copies the request into the item, completing name and price from the catalog when a refId is given. */
    private void apply(ItineraryItem item, ItemRequest r, String destination) {
        CatalogItem c = r.refId() == null ? null
                : catalogProvider.find(r.type(), r.refId(), destination).orElse(null);
        String name = r.name() != null && !r.name().isBlank() ? r.name() : c != null ? c.name() : null;
        if (name == null) {
            throw new IllegalArgumentException("name is required for an item that is not in the catalog");
        }
        double unitPrice = r.unitPrice() != null ? r.unitPrice() : c != null ? c.price() : 0;
        item.setType(r.type());
        item.setRefId(c != null ? c.id() : null);
        item.setName(name);
        item.setCategory(r.category() != null ? r.category() : c != null ? c.category() : null);
        item.setStartTime(r.startTime());
        item.setEndTime(r.endTime());
        item.setPricing(unitPrice, r.quantity() != null ? r.quantity() : 1);
        item.setNotes(r.notes());
    }

    private ItineraryDto saveWithTotal(Itinerary it) {
        it.recalculateTotal();
        return ItineraryDto.from(itineraryRepository.save(it));
    }

    Itinerary findItinerary(Long id) {
        return itineraryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Itinerary " + id + " not found"));
    }

    private ItineraryItem findItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Item " + id + " not found"));
    }
}
