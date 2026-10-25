package com.travelmate.itinerary.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "itinerary_day")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id")
    private Itinerary itinerary;

    private int dayNumber;
    private LocalDate date;
    private String title;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC")
    private List<ItineraryItem> items = new ArrayList<>();

    public ItineraryDay(int dayNumber, LocalDate date, String title) {
        this.dayNumber = dayNumber;
        this.date = date;
        this.title = title;
    }

    public void addItem(ItineraryItem item) {
        item.setDay(this);
        items.add(item);
    }
}
