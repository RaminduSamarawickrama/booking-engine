package com.booking.payment.service.event;

import com.booking.platform.messaging.DomainEvent;

/**
 * payment.succeeded (booking-service confirms the booking), payment.failed and
 * payment.refunded. Amounts are in minor units.
 */
public record PaymentEvent(String type, String paymentId, String bookingId, String bookingReference, int amountMinor,
        String currency, String provider, String reason) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "payment";
    }

    @Override
    public String aggregateId() {
        return paymentId;
    }
}
