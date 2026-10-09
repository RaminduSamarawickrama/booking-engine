package com.booking.booking.service.event;

import java.time.Instant;

import com.booking.platform.messaging.DomainEvent;

/**
 * What booking-service publishes: booking.created, booking.confirmed, booking.cancelled,
 * booking.expired and booking.payment_rejected (a payment arrived for a booking that can no
 * longer take it, so payment-service refunds it). Notification-service turns these into
 * emails; dispatch starts from booking.confirmed.
 */
public record BookingEvent(
        String type,
        String bookingId,
        String reference,
        String status,
        String customerName,
        String customerEmail,
        String userId,
        Instant pickupAt,
        int totalMinor,
        String currency,
        String paymentId,
        String reason) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "booking";
    }

    @Override
    public String aggregateId() {
        return bookingId;
    }
}
