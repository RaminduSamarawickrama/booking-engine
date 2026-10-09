package com.booking.payment.client;

import java.util.List;
import java.util.Locale;

import com.booking.payment.domain.Payment;
import com.booking.stripe.payments.BookingPayment;
import com.booking.stripe.payments.StripePaymentsGateway;

/**
 * Stripe Checkout Sessions in embedded (Elements) mode. The browser shows Stripe's Payment
 * Element with the client secret; the booking is confirmed only by the signed webhook.
 */
public class StripePaymentProvider implements PaymentProvider {

    public static final String NAME = "stripe";

    private final StripePaymentsGateway gateway;
    private final String publishableKey;
    private final String returnUrlTemplate;

    public StripePaymentProvider(StripePaymentsGateway gateway, String publishableKey, String returnUrlTemplate) {
        this.gateway = gateway;
        this.publishableKey = publishableKey;
        this.returnUrlTemplate = returnUrlTemplate;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Checkout start(Payment payment, String description) {
        BookingPayment request = new BookingPayment(payment.bookingId().toString(), payment.bookingReference(),
                payment.attempt(), payment.currency().toLowerCase(Locale.ROOT), payment.customerEmail(),
                List.of(new BookingPayment.Leg(payment.bookingId().toString(), description, payment.amountMinor())));
        String returnUrl = returnUrlTemplate.replace("{REFERENCE}", payment.bookingReference());
        StripePaymentsGateway.CheckoutSessionResult session = gateway.createWebCheckout(request, returnUrl);
        return new Checkout(session.sessionId(), session.clientSecret(), publishableKey);
    }

    @Override
    public String refund(Payment payment, String reason) {
        if (payment.providerPaymentId() == null) {
            throw new IllegalStateException("Payment " + payment.id() + " has no PaymentIntent to refund");
        }
        return gateway.refund(payment.providerPaymentId(), null, "payment-" + payment.id(), reason);
    }
}
