package com.travelmate.itinerary.service;

import com.travelmate.itinerary.dto.BudgetSummaryDto;
import com.travelmate.itinerary.dto.BudgetSummaryDto.CategoryBudget;
import com.travelmate.itinerary.entity.Expense;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.entity.Itinerary;
import com.travelmate.itinerary.entity.ItineraryDay;
import com.travelmate.itinerary.entity.ItineraryItem;
import com.travelmate.itinerary.integration.TripInfo;
import com.travelmate.itinerary.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cost estimation and budget tracking maths. */
class BudgetServiceTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 10);

    @Test
    void hotelCostIsPricePerNightTimesNights() {
        ItineraryItem hotel = item(ItemType.HOTEL, 95, 4);

        assertThat(hotel.getEstimatedCost()).isEqualTo(380);
    }

    @Test
    void quantityIsAtLeastOne() {
        ItineraryItem meal = item(ItemType.RESTAURANT, 20, 0);

        assertThat(meal.getQuantity()).isEqualTo(1);
        assertThat(meal.getEstimatedCost()).isEqualTo(20);
    }

    @Test
    void tripOfFiveDaysHasFourNightsAndOneDayTripHasOne() {
        TripInfo fiveDays = new TripInfo(1L, 1L, "Rome", DAY_1, DAY_1.plusDays(4), 800, List.of());
        TripInfo oneDay = new TripInfo(2L, 1L, "Rome", DAY_1, DAY_1, 100, List.of());

        assertThat(fiveDays.numberOfDays()).isEqualTo(5);
        assertThat(fiveDays.numberOfNights()).isEqualTo(4);
        assertThat(oneDay.numberOfNights()).isEqualTo(1);
    }

    @Test
    void summaryComparesEstimatedCostsAndRealExpensesPerCategory() {
        Itinerary it = new Itinerary();
        it.setBudget(300);
        ItineraryDay day = new ItineraryDay(1, DAY_1, "Day 1");
        it.addDay(day);
        day.addItem(item(ItemType.HOTEL, 100, 2));
        day.addItem(item(ItemType.ACTIVITY, 30, 1));
        day.addItem(item(ItemType.RESTAURANT, 20, 1));
        it.recalculateTotal();
        it.getExpenses().add(expense(ItemType.HOTEL, 200));
        it.getExpenses().add(expense(ItemType.RESTAURANT, 25));

        ItineraryService itineraryService = mock(ItineraryService.class);
        when(itineraryService.findItinerary(1L)).thenReturn(it);
        BudgetService service = new BudgetService(itineraryService, mock(ExpenseRepository.class));

        BudgetSummaryDto summary = service.summary(1L);

        assertThat(summary.estimatedTotal()).isEqualTo(250);
        assertThat(summary.spentTotal()).isEqualTo(225);
        assertThat(summary.remainingEstimated()).isEqualTo(50);
        assertThat(summary.remainingActual()).isEqualTo(75);
        assertThat(summary.overBudget()).isFalse();
        // Categories without any cost (transport, custom) are left out
        assertThat(summary.categories()).containsExactly(
                new CategoryBudget(ItemType.ACTIVITY, 30, 0),
                new CategoryBudget(ItemType.RESTAURANT, 20, 25),
                new CategoryBudget(ItemType.HOTEL, 200, 200));
    }

    @Test
    void summaryFlagsAnItineraryOverBudget() {
        Itinerary it = new Itinerary();
        it.setBudget(100);
        ItineraryDay day = new ItineraryDay(1, DAY_1, "Day 1");
        it.addDay(day);
        day.addItem(item(ItemType.HOTEL, 150, 1));
        it.recalculateTotal();

        ItineraryService itineraryService = mock(ItineraryService.class);
        when(itineraryService.findItinerary(1L)).thenReturn(it);

        BudgetSummaryDto summary = new BudgetService(itineraryService, mock(ExpenseRepository.class)).summary(1L);

        assertThat(summary.overBudget()).isTrue();
        assertThat(summary.remainingEstimated()).isEqualTo(-50);
    }

    private static ItineraryItem item(ItemType type, double price, int quantity) {
        ItineraryItem item = new ItineraryItem();
        item.setType(type);
        item.setName(type.name());
        item.setPricing(price, quantity);
        return item;
    }

    private static Expense expense(ItemType category, double amount) {
        Expense e = new Expense();
        e.setCategory(category);
        e.setAmount(amount);
        e.setDate(DAY_1);
        return e;
    }
}
