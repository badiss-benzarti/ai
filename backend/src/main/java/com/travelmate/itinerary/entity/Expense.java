package com.travelmate.itinerary.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Money actually spent during the trip (budget tracking). */
@Entity
@Table(name = "expense")
@Getter
@Setter
@NoArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id")
    private Itinerary itinerary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType category;

    private double amount;
    private LocalDate date;
    private String description;
}
