package com.travelmate.itinerary.dto;

import com.travelmate.itinerary.entity.Expense;
import com.travelmate.itinerary.entity.ItemType;

import java.time.LocalDate;

public record ExpenseDto(Long id, ItemType category, double amount, LocalDate date, String description) {

    public static ExpenseDto from(Expense e) {
        return new ExpenseDto(e.getId(), e.getCategory(), e.getAmount(), e.getDate(), e.getDescription());
    }
}
