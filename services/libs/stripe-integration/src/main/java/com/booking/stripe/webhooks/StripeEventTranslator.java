package com.booking.stripe.webhooks;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Verifies Stripe webhook signatures and translates events into {@link PaymentSignal}s.
 *
 * <p>Rules this class enforces:
 * <ul>
 *   <li>No event is read before its signature is verified with the endpoint's signing secret.
 *   <li>Web bookings are confirmed from {@code checkout.session.completed} and
 *       {@code checkout.session.async_payment_succeeded}, only when payment_status is not
 *       "unpaid" (delayed payment methods complete the session while still unpaid).
 *   <li>Mobile bookings are confirmed from {@code payment_intent.succeeded}.
 *   <li>The return or success page never confirms anything.
 * </ul>
 */
public final class StripeEventTranslator {

  private final StripeClient stripe;
  private final String signingSecret;

  public StripeEventTranslator(StripeClient stripe, String signingSecret) {
    this.stripe = Objects.requireNonNull(stripe);
    if (signingSecret == null || !signingSecret.startsWith("whsec_")) {
      throw new IllegalArgumentException("webhook signing secret (whsec_...) is required");
    }
    this.signingSecret = signingSecret;
  }

  /**
   * @param rawPayload the request body exactly as received (no JSON re-serialisation)
   * @param signatureHeader the Stripe-Signature header
   * @throws InvalidSignatureException respond 400 and do nothing else
   */
  public List<PaymentSignal> translate(String rawPayload, String signatureHeader) {
    Event event;
    try {
      event = stripe.constructEvent(rawPayload, signatureHeader, signingSecret);
    } catch (SignatureVerificationException e) {
      throw new InvalidSignatureException(e);
    }
    return translate(event);
  }

  List<PaymentSignal> translate(Event event) {
    String id = event.getId();
    return switch (event.getType()) {
      case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
        Session session = object(event, Session.class);
        if ("unpaid".equals(session.getPaymentStatus())) {
          yield List.of(new PaymentSignal.Ignored(id, event.getType() + " (awaiting async payment)"));
        }
        yield List.of(new PaymentSignal.BookingPaid(
            id, meta(session.getMetadata(), "booking_id"), meta(session.getMetadata(), "booking_reference"),
            session.getPaymentIntent(), "web"));
      }
      case "checkout.session.async_payment_failed" -> {
        Session session = object(event, Session.class);
        yield List.of(new PaymentSignal.BookingPaymentFailed(
            id, meta(session.getMetadata(), "booking_id"), session.getPaymentIntent(), "async payment failed"));
      }
      case "payment_intent.succeeded" -> {
        PaymentIntent intent = object(event, PaymentIntent.class);
        String bookingId = meta(intent.getMetadata(), "booking_id");
        PaymentSignal charge = new PaymentSignal.ChargeAvailable(
            id, bookingId, intent.getId(), intent.getLatestCharge(), nz(intent.getAmountReceived()));
        if ("mobile".equals(meta(intent.getMetadata(), "channel"))) {
          yield List.of(
              new PaymentSignal.BookingPaid(
                  id, bookingId, meta(intent.getMetadata(), "booking_reference"), intent.getId(), "mobile"),
              charge);
        }
        yield List.of(charge);
      }
      case "payment_intent.payment_failed" -> {
        PaymentIntent intent = object(event, PaymentIntent.class);
        String reason = intent.getLastPaymentError() != null ? intent.getLastPaymentError().getCode() : null;
        yield List.of(new PaymentSignal.BookingPaymentFailed(
            id, meta(intent.getMetadata(), "booking_id"), intent.getId(), reason));
      }
      case "charge.refunded" -> {
        Charge charge = object(event, Charge.class);
        yield List.of(new PaymentSignal.ChargeRefunded(
            id, charge.getPaymentIntent(), charge.getId(), nz(charge.getAmountRefunded())));
      }
      case "charge.dispute.created" -> {
        Dispute dispute = object(event, Dispute.class);
        yield List.of(new PaymentSignal.DisputeOpened(
            id, dispute.getId(), dispute.getCharge(), dispute.getPaymentIntent(), nz(dispute.getAmount()),
            dispute.getReason()));
      }
      case "charge.dispute.closed" -> {
        Dispute dispute = object(event, Dispute.class);
        yield List.of(new PaymentSignal.DisputeClosed(id, dispute.getId(), dispute.getCharge(), dispute.getStatus()));
      }
      case "invoice.paid" -> {
        Invoice invoice = object(event, Invoice.class);
        yield List.of(new PaymentSignal.InvoicePaid(id, invoice.getId(), invoice.getCustomer(), nz(invoice.getAmountPaid())));
      }
      case "invoice.payment_failed", "invoice.overdue" -> {
        Invoice invoice = object(event, Invoice.class);
        yield List.of(new PaymentSignal.InvoicePaymentFailed(id, invoice.getId(), invoice.getCustomer()));
      }
      default -> List.of(new PaymentSignal.Ignored(id, event.getType()));
    };
  }

  private static <T extends StripeObject> T object(Event event, Class<T> type) {
    StripeObject object =
        event.getDataObjectDeserializer().getObject()
            .orElseThrow(() -> new IllegalStateException(
                "Cannot read event " + event.getId() + " (API version " + event.getApiVersion()
                    + "). Set the webhook endpoint's API version to match the stripe-java SDK."));
    if (!type.isInstance(object)) {
      throw new IllegalStateException("Event " + event.getId() + " carries " + object.getClass().getSimpleName());
    }
    return type.cast(object);
  }

  private static String meta(Map<String, String> metadata, String key) {
    return metadata == null ? null : metadata.get(key);
  }

  private static long nz(Long value) {
    return value == null ? 0L : value;
  }

  /** The request did not come from Stripe (or the secret is wrong). Respond 400. */
  public static final class InvalidSignatureException extends RuntimeException {
    InvalidSignatureException(Throwable cause) {
      super("Stripe webhook signature verification failed", cause);
    }
  }
}
