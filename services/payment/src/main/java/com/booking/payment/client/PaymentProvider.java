package com.booking.payment.client;

import com.booking.payment.domain.Payment;

/** Where the money is taken: the mock provider in development, Stripe in test (or live) mode. */
public interface PaymentProvider {

    String name();

    /** What the browser needs to show the payment form. */
    record Checkout(String providerReference, String clientSecret, String publishableKey) {
    }

    Checkout start(Payment payment, String description);

    /** Returns the provider's refund id. */
    String refund(Payment payment, String reason);
}
