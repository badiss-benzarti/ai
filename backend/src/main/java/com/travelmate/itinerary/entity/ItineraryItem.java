package com.travelmate.itinerary.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "itinerary_item")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id")
    private ItineraryDay day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType type;

    /** Id of the activity / restaurant / hotel in its own module, null for custom items. */
    private Long refId;

    @Column(nullable = false)
    private String name;
    private String category;

    private LocalTime startTime;
    private LocalTime endTime;

    /** Price of one unit (one ticket, one meal, one night). */
    private double unitPrice;
    /** Number of units, e.g. hotel nights. */
    private int quantity = 1;
    private double estimatedCost;

    @Column(length = 1000)
    private String notes;

    public void setPricing(double unitPrice, int quantity) {
        this.unitPrice = unitPrice;
        this.quantity = Math.max(1, quantity);
        this.estimatedCost = unitPrice * this.quantity;
    }
}
