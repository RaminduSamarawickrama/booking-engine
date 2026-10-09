package com.booking.payment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * PENDING --provider says paid--> SUCCEEDED --refunded--> REFUNDED
 *    '----declined / abandoned--> FAILED
 * </pre>
 * A second success for an already-paid booking goes straight to REFUNDED.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> NEXT = Map.of(
            PENDING, EnumSet.of(SUCCEEDED, FAILED, REFUNDED),
            SUCCEEDED, EnumSet.of(REFUNDED),
            FAILED, EnumSet.of(SUCCEEDED),          // a late webhook can still report success
            REFUNDED, EnumSet.noneOf(PaymentStatus.class));

    public boolean canMoveTo(PaymentStatus next) {
        return NEXT.get(this).contains(next);
    }
}
