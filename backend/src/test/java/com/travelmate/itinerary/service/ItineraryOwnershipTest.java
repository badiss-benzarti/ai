package com.travelmate.itinerary.service;

import com.travelmate.itinerary.dto.ItemRequest;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.entity.Itinerary;
import com.travelmate.itinerary.entity.ItineraryDay;
import com.travelmate.itinerary.entity.ItineraryItem;
import com.travelmate.itinerary.integration.CatalogProvider;
import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.integration.TripInfoProvider;
import com.travelmate.itinerary.repository.ItineraryDayRepository;
import com.travelmate.itinerary.repository.ItineraryItemRepository;
import com.travelmate.itinerary.repository.ItineraryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** A user only reaches their own trips and itineraries; someone else's answer 404. */
class ItineraryOwnershipTest {

    private static final long ME = 1L;
    private static final long SOMEONE_ELSE = 2L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 10);

    private ItineraryRepository itineraries;
    private ItineraryItemRepository items;
    private ItineraryDayRepository days;
    private TripInfoProvider trips;
    private AiItineraryService ai;
    private ItineraryService service;

    @BeforeEach
    void setUp() {
        itineraries = mock(ItineraryRepository.class);
        items = mock(ItineraryItemRepository.class);
        days = mock(ItineraryDayRepository.class);
        trips = mock(TripInfoProvider.class);
        ai = mock(AiItineraryService.class);
        service = new ItineraryService(itineraries, days, items, trips, mock(CatalogProvider.class), ai, () -> ME);
    }

    @Test
    void readsMyOwnItinerary() {
        stored(10L, ME);

        assertThat(service.getById(10L).id()).isEqualTo(10L);
    }

    @Test
    void someoneElsesItineraryLooksMissing() {
        stored(20L, SOMEONE_ELSE);

        assertThatThrownBy(() -> service.getById(20L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.delete(20L)).isInstanceOf(EntityNotFoundException.class);
        verify(itineraries, never()).delete(any());
    }

    @Test
    void cannotEditAnItemOfSomeoneElsesItinerary() {
        Itinerary theirs = stored(20L, SOMEONE_ELSE);
        ItineraryItem item = new ItineraryItem();
        item.setId(5L);
        theirs.getDays().get(0).addItem(item);
        when(items.findById(5L)).thenReturn(Optional.of(item));

        ItemRequest request = new ItemRequest(ItemType.CUSTOM, null, "Taxi", null, null, null, 30.0, 1, null);
        assertThatThrownBy(() -> service.updateItem(5L, request)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.deleteItem(5L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void cannotGenerateForSomeoneElsesTrip() {
        when(trips.getTrip(7L)).thenReturn(new TripInfo(7L, SOMEONE_ELSE, "Rome", DAY_1, DAY_1.plusDays(2), 500, List.of()));

        assertThatThrownBy(() -> service.generate(7L)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void listsOnlyMyTripsAndItineraries() {
        service.getMyTrips();
        service.getMine();

        verify(trips).getTripsForUser(ME);
        verify(itineraries).findByUserIdOrderByCreatedAtDesc(ME);
    }

    private Itinerary stored(long id, long userId) {
        Itinerary it = new Itinerary();
        it.setId(id);
        it.setUserId(userId);
        it.setTripId(1L);
        it.setDestination("Rome");
        it.setStartDate(DAY_1);
        it.setEndDate(DAY_1);
        it.setBudget(500);
        it.addDay(new ItineraryDay(1, DAY_1, "Day 1"));
        when(itineraries.findById(id)).thenReturn(Optional.of(it));
        return it;
    }
}
