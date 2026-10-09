package com.booking.booking.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record Booking(
        UUID id,
        String reference,
        BookingStatus status,
        UUID userId,
        String customerName,
        String customerEmail,
        String customerPhone,
        JsonNode pickup,
        JsonNode dropoff,
        Instant pickupAt,
        int passengers,
        int luggage,
        String flightNumber,
        String driverNotes,
        String categoryCode,
        String currency,
        int vehiclePriceMinor,
        int extrasTotalMinor,
        int totalMinor,
        int distanceMeters,
        int durationSeconds,
        Instant createdAt,
        List<ExtraLine> extras) {

    public record ExtraLine(String code, int quantity, int unitPriceMinor) {
        public int totalMinor() {
            return quantity * unitPriceMinor;
        }
    }
}
