package com.booking.payment.client;

import com.booking.payment.domain.Payment;

/**
 * Pretends to be a card processor. The customer types a test card into our own form and
 * payment-service decides the outcome (see MockCards). Never charges anything.
 */
public class MockPaymentProvider implements PaymentProvider {

    public static final String NAME = "mock";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Checkout start(Payment payment, String description) {
        return new Checkout("mock_" + payment.id(), null, null);
    }

    @Override
    public String refund(Payment payment, String reason) {
        return "mock_refund_" + payment.id();
    }
}
