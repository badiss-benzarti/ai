package com.travelmate.itinerary.service;

import com.travelmate.itinerary.dto.GenerationResultDto;
import com.travelmate.itinerary.dto.ItineraryDto;
import com.travelmate.itinerary.dto.OptimizationResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Full AI flow of Module 6: generate the itinerary, then, if it is over budget,
 * automatically replace items with cheaper alternatives.
 */
@Service
@RequiredArgsConstructor
public class TripPlanningService {

    private final ItineraryService itineraryService;
    private final BudgetOptimizerService optimizerService;

    public GenerationResultDto plan(Long tripId) {
        ItineraryDto generated = itineraryService.generate(tripId);
        if (!generated.overBudget()) {
            return new GenerationResultDto(generated, null);
        }
        OptimizationResultDto optimization = optimizerService.optimize(generated.id());
        return new GenerationResultDto(optimization.itinerary(), optimization);
    }
}
