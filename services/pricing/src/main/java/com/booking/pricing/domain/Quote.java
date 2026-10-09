package com.booking.pricing.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


/** A priced journey, stored so the booking pays exactly what the customer was shown. */
public record Quote(UUID id, Instant createdAt, Instant expiresAt, String currency, OffsetDateTime pickupAt,
        Place pickup, Place dropoff, int passengers, int luggage, int distanceMeters, int durationSeconds,
        List<QuoteCalculator.Option> options, List<ExtraPrice> extras) {

    public record ExtraPrice(String code, int priceMinor) {
    }
}
