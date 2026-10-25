package com.travelmate.itinerary.dto;

import java.util.List;

public record OptimizationResultDto(ItineraryDto itinerary,
                                    double budget,
                                    double totalBefore,
                                    double totalAfter,
                                    boolean withinBudget,
                                    boolean aiUsed,
                                    List<ReplacementDto> replacements,
                                    String message) {
}
