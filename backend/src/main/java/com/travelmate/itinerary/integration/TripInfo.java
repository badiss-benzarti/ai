package com.travelmate.itinerary.integration;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** What Module 6 needs to know about a trip (owned by Module 2). */
public record TripInfo(Long id,
                       Long userId,
                       String destination,
                       LocalDate startDate,
                       LocalDate endDate,
                       double budget,
                       List<String> interests) {

    public int numberOfDays() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** Hotel nights = days - 1 (minimum 1). */
    public int numberOfNights() {
        return Math.max(1, numberOfDays() - 1);
    }
}
