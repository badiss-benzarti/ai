package com.travelmate.itinerary.integration;

/**
 * The logged-in user (Module 1).
 * INTEGRATION: replace MockCurrentUserProvider with an implementation that reads the user id
 * from Module 1's JWT, e.g. from {@code SecurityContextHolder.getContext().getAuthentication()}.
 */
public interface CurrentUserProvider {

    Long currentUserId();
}
