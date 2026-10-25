package com.travelmate.itinerary.repository;

import com.travelmate.itinerary.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByItineraryIdOrderByDateAsc(Long itineraryId);
}
