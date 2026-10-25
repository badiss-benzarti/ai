package com.travelmate.itinerary.dto;

/**
 * Result of the AI generation.
 * optimization is null when the generated itinerary was already within budget.
 */
public record GenerationResultDto(ItineraryDto itinerary, OptimizationResultDto optimization) {
}
