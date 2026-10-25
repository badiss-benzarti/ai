package com.travelmate.itinerary.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "itinerary")
@Getter
@Setter
@NoArgsConstructor
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Trip id from Module 2 (no foreign key, so the module works on its own). */
    @Column(nullable = false)
    private Long tripId;

    /** User id from Module 1. */
    @Column(nullable = false)
    private Long userId;

    // Copied from the trip so the itinerary and its PDF stay readable on their own
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private double budget;

    private double totalEstimatedCost;
    private boolean generatedByAi;
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<ItineraryDay> days = new ArrayList<>();

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("date ASC")
    private List<Expense> expenses = new ArrayList<>();

    public void addDay(ItineraryDay day) {
        day.setItinerary(this);
        days.add(day);
    }

    public List<ItineraryItem> allItems() {
        return days.stream().flatMap(d -> d.getItems().stream()).toList();
    }

    public void recalculateTotal() {
        totalEstimatedCost = allItems().stream().mapToDouble(ItineraryItem::getEstimatedCost).sum();
    }
}
