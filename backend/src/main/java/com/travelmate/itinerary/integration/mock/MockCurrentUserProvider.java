package com.travelmate.itinerary.integration.mock;

import com.travelmate.itinerary.integration.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Until Module 1 (JWT) is integrated, every request belongs to the same user (user 1 by default). */
@Component
public class MockCurrentUserProvider implements CurrentUserProvider {

    private final Long userId;

    public MockCurrentUserProvider(@Value("${app.mock-user-id:1}") Long userId) {
        this.userId = userId;
    }

    @Override
    public Long currentUserId() {
        return userId;
    }
}
