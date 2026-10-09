package com.booking.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import com.booking.payment.client.BookingClient;
import com.booking.payment.client.MockPaymentProvider;
import com.booking.payment.client.PaymentProvider;
import com.booking.payment.domain.MockCards;
import com.booking.payment.domain.Payment;
import com.booking.payment.domain.PaymentStatus;
import com.booking.payment.repository.PaymentRepository;
import com.booking.payment.service.event.PaymentEvent;
import com.booking.platform.error.ApiException;
import com.booking.platform.messaging.Inbox;
import com.booking.platform.messaging.Outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes payment for a booking. The amount always comes from booking-service, never from the
 * browser; the booking is confirmed by the payment.succeeded event, never by a page redirect.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final BookingClient bookings;
    private final PaymentProvider provider;
    private final Outbox outbox;
    private final Inbox inbox;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, BookingClient bookings, PaymentProvider provider, Outbox outbox,
            Inbox inbox, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.provider = provider;
        this.outbox = outbox;
        this.inbox = inbox;
        this.clock = clock;
    }

    public record Started(Payment payment, String clientSecret, String publishableKey) {
    }

    /** Starts a payment attempt for a booking the caller is allowed to see. */
    @Transactional
    public Started start(String reference, String manageToken, String authorization) {
        BookingClient.BookingSummary booking = bookings.find(reference, manageToken, authorization)
                .orElseThrow(() -> ApiException.notFound("booking_not_found",
                        "We couldn't find that booking. Open it from your confirmation link."));
        if (payments.hasSucceeded(booking.bookingId()) || "CONFIRMED".equals(booking.status())) {
            throw ApiException.conflict("already_paid", "This booking is already paid.");
        }
        if (!"PENDING_PAYMENT".equals(booking.status())) {
            throw ApiException.conflict("booking_not_payable",
                    "This booking is " + booking.status().toLowerCase(Locale.ROOT).replace('_', ' ') + " and can't be paid.");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Payment payment = new Payment(UUID.randomUUID(), booking.bookingId(), booking.reference(),
                payments.nextAttempt(booking.bookingId()), booking.totalMinor(), booking.currency(),
                booking.customerEmail(), provider.name(), null, null, PaymentStatus.PENDING, null, null, now);
        payments.insert(payment);
        PaymentProvider.Checkout checkout = provider.start(payment, "Airport transfer " + booking.reference());
        payments.setProviderReference(payment.id(), checkout.providerReference(), now);
        return new Started(payments.findById(payment.id()).orElseThrow(), checkout.clientSecret(), checkout.publishableKey());
    }

    /** Payment ids are random UUIDs, so holding one is what grants access to it. */
    @Transactional(readOnly = true)
    public Payment get(UUID paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("payment_not_found", "That payment doesn't exist."));
    }

    /** The mock provider's card form. Only available when the mock provider is active. */
    @Transactional
    public Payment confirmMock(UUID paymentId, String cardNumber) {
        if (!MockPaymentProvider.NAME.equals(provider.name())) {
            throw ApiException.notFound("payment_not_found", "That payment doesn't exist.");
        }
        Payment payment = payments.lockById(paymentId)
                .orElseThrow(() -> ApiException.notFound("payment_not_found", "That payment doesn't exist."));
        if (payment.status() != PaymentStatus.PENDING) {
            throw ApiException.conflict("payment_finished", "This payment is already " + payment.status().name().toLowerCase(Locale.ROOT) + ".");
        }
        MockCards.Outcome outcome = MockCards.charge(cardNumber);
        if (outcome.approved()) {
            succeeded(payment, "mock_pi_" + payment.id());
        } else {
            failed(payment, outcome.code(), outcome.message());
        }
        return payments.findById(paymentId).orElseThrow();
    }

    /** Marks the newest attempt for a booking as paid (provider webhook). Idempotent. */
    @Transactional
    public void providerReportedPaid(UUID bookingId, String providerPaymentId) {
        payments.lockLatestFor(bookingId, provider.name()).ifPresentOrElse(
                payment -> succeeded(payment, providerPaymentId),
                () -> log.warn("Provider reported a payment for booking {} with no attempt on record", bookingId));
    }

    @Transactional
    public void providerReportedFailed(UUID bookingId, String code) {
        payments.lockLatestFor(bookingId, provider.name()).ifPresent(payment -> {
            if (payment.status() == PaymentStatus.PENDING) {
                failed(payment, code == null ? "payment_failed" : code, "The payment didn't go through.");
            }
        });
    }

    @Transactional
    public void providerReportedRefund(String providerPaymentId) {
        payments.lockByProviderPaymentId(providerPaymentId).ifPresent(payment -> {
            if (payment.status() == PaymentStatus.SUCCEEDED
                    && payments.updateStatus(payment.id(), PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED, null, null,
                            null, clock.instant())) {
                outbox.append(event("payment.refunded", payment, "refunded at provider"));
            }
        });
    }

    /**
     * booking-service rejected a payment (the booking expired or was cancelled meanwhile, or
     * the amount no longer matches): give the money back. Applied once per event.
     */
    @Transactional
    public void bookingRejectedPayment(UUID eventId, String consumer, UUID paymentId, String reason) {
        if (!inbox.firstDelivery(eventId, consumer)) {
            return;
        }
        payments.lockById(paymentId).ifPresent(payment -> {
            if (payment.status() != PaymentStatus.SUCCEEDED) {
                return;
            }
            String refundId = provider.refund(payment, "booking rejected payment: " + reason);
            if (payments.updateStatus(payment.id(), PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED, null, null, reason,
                    clock.instant())) {
                log.info("Refunded payment {} for booking {} ({}): {}", payment.id(), payment.bookingReference(), refundId, reason);
                outbox.append(event("payment.refunded", payment, reason));
            }
        });
    }

    private void succeeded(Payment payment, String providerPaymentId) {
        if (payment.status() == PaymentStatus.SUCCEEDED || payment.status() == PaymentStatus.REFUNDED) {
            return;
        }
        Instant now = clock.instant();
        if (payments.hasSucceeded(payment.bookingId())) {
            // Paid twice (two tabs, two devices): keep the first payment, return this one.
            payments.updateStatus(payment.id(), payment.status(), PaymentStatus.REFUNDED, providerPaymentId,
                    "duplicate_payment", "The booking was already paid; this payment was refunded.", now);
            Payment duplicate = payments.findById(payment.id()).orElseThrow();
            provider.refund(duplicate, "duplicate payment");
            outbox.append(event("payment.refunded", duplicate, "duplicate payment"));
            return;
        }
        payments.updateStatus(payment.id(), payment.status(), PaymentStatus.SUCCEEDED, providerPaymentId, null, null, now);
        outbox.append(event("payment.succeeded", payment, null));
    }

    private void failed(Payment payment, String code, String message) {
        payments.updateStatus(payment.id(), PaymentStatus.PENDING, PaymentStatus.FAILED, null, code, message, clock.instant());
        outbox.append(event("payment.failed", payment, code));
    }

    private static PaymentEvent event(String type, Payment p, String reason) {
        return new PaymentEvent(type, p.id().toString(), p.bookingId().toString(), p.bookingReference(), p.amountMinor(),
                p.currency(), p.provider(), reason);
    }
}
