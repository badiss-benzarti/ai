package com.travelmate.itinerary.dto;

import jakarta.validation.constraints.NotNull;

public record GenerateRequest(@NotNull Long tripId) {
}
