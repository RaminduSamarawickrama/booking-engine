package com.booking.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** One attempt to pay for a booking. Amounts are in minor units (pence). */
public record Payment(
        UUID id,
        UUID bookingId,
        String bookingReference,
        int attempt,
        int amountMinor,
        String currency,
        String customerEmail,
        String provider,
        String providerReference,
        String providerPaymentId,
        PaymentStatus status,
        String failureCode,
        String failureMessage,
        Instant createdAt) {
}
