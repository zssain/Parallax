package com.parallax.application.review;

/**
 * Body of {@code POST /api/v1/reviews/{id}} (SPEC §15). Business rules (note length, limit bounds,
 * DECLINED forcing limit 0) are enforced in {@link ReviewService}, not by Bean Validation, so each
 * rule returns its own 422 message.
 */
public record ReviewRequest(String decision, Integer creditLimit, String overrideCode, String note) {
}
