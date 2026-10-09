package com.booking.payment.service;

import java.util.UUID;

import com.booking.platform.error.ApiException;
import com.booking.platform.messaging.Inbox;
import com.booking.stripe.webhooks.PaymentSignal;
import com.booking.stripe.webhooks.StripeEventTranslator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies Stripe's signature and applies each event once. Only signed webhooks move money
 * states; the browser coming back from Stripe proves nothing.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);
    private static final String CONSUMER = "stripe-webhook";

    private final ObjectProvider<StripeEventTranslator> translator;
    private final PaymentService payments;
    private final Inbox inbox;

    public StripeWebhookService(ObjectProvider<StripeEventTranslator> translator, PaymentService payments, Inbox inbox) {
        this.translator = translator;
        this.payments = payments;
        this.inbox = inbox;
    }

    @Transactional
    public void handle(String payload, String signature) {
        StripeEventTranslator stripe = translator.getIfAvailable();
        if (stripe == null) {
            throw ApiException.notFound("stripe_disabled", "Stripe payments are not enabled.");
        }
        java.util.List<PaymentSignal> signals;
        try {
            signals = stripe.translate(payload, signature);
        } catch (StripeEventTranslator.InvalidSignatureException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_signature",
                    "Webhook signature verification failed.");
        }
        for (PaymentSignal signal : signals) {
            UUID dedupeId = UUID.nameUUIDFromBytes((signal.eventId() + ":" + signal.getClass().getSimpleName()).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            if (!inbox.firstDelivery(dedupeId, CONSUMER)) {
                continue;
            }
            switch (signal) {
                case PaymentSignal.BookingPaid paid -> payments.providerReportedPaid(UUID.fromString(paid.bookingId()),
                        paid.paymentIntentId());
                case PaymentSignal.BookingPaymentFailed failed -> payments.providerReportedFailed(
                        UUID.fromString(failed.bookingId()), failed.reason());
                case PaymentSignal.ChargeRefunded refunded -> payments.providerReportedRefund(refunded.paymentIntentId());
                default -> log.debug("Stripe event {} needs no action here: {}", signal.eventId(), signal);
            }
        }
    }
}
