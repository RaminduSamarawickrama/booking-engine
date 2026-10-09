package com.booking.booking.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where a booking is in its life. Ride states (driver assigned, en route, ...) arrive with
 * dispatch; the payment-facing part is complete here.
 *
 * <pre>
 * PENDING_PAYMENT --payment succeeded--> CONFIRMED --cancelled--> CANCELLED
 *        |--payment window passed--> EXPIRED
 *        '--customer abandoned-----> CANCELLED
 * </pre>
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    private static final Map<BookingStatus, Set<BookingStatus>> NEXT = Map.of(
            PENDING_PAYMENT, EnumSet.of(CONFIRMED, CANCELLED, EXPIRED),
            CONFIRMED, EnumSet.of(CANCELLED),
            CANCELLED, EnumSet.noneOf(BookingStatus.class),
            EXPIRED, EnumSet.noneOf(BookingStatus.class));

    public boolean canMoveTo(BookingStatus next) {
        return NEXT.get(this).contains(next);
    }

    public boolean isFinal() {
        return NEXT.get(this).isEmpty();
    }
}
