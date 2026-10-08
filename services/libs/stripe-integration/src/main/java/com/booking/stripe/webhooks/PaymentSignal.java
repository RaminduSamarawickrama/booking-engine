package com.booking.stripe.webhooks;

/**
 * What a verified Stripe event means for our domain. payment-service turns these into its own
 * events (PaymentCompleted, PaymentFailed, ...) via the transactional outbox.
 *
 * <p>Every signal carries the Stripe event ID; consumers record it to ignore redeliveries.
 */
public sealed interface PaymentSignal {

  String eventId();

  /** Booking paid: confirm it. Arrives from web (Checkout) or mobile (PaymentIntent). */
  record BookingPaid(String eventId, String bookingId, String bookingReference, String paymentIntentId, String channel)
      implements PaymentSignal {}

  /** The charge to name as source_transaction for later driver transfers. */
  record ChargeAvailable(String eventId, String bookingId, String paymentIntentId, String chargeId, long amountReceivedMinor)
      implements PaymentSignal {}

  /** Payment failed; the customer may retry until the booking hold expires. */
  record BookingPaymentFailed(String eventId, String bookingId, String paymentIntentId, String reason)
      implements PaymentSignal {}

  /** A refund completed (fully or partly). */
  record ChargeRefunded(String eventId, String paymentIntentId, String chargeId, long amountRefundedMinor)
      implements PaymentSignal {}

  /** A customer disputed a charge: reverse the related driver transfers and alert ops. */
  record DisputeOpened(String eventId, String disputeId, String chargeId, String paymentIntentId, long amountMinor, String reason)
      implements PaymentSignal {}

  /** Disputes we did not win need follow-up (recovery may already have happened at opening). */
  record DisputeClosed(String eventId, String disputeId, String chargeId, String status) implements PaymentSignal {}

  /** A corporate invoice was paid. */
  record InvoicePaid(String eventId, String invoiceId, String customerId, long amountPaidMinor) implements PaymentSignal {}

  /** A corporate invoice payment failed or the invoice became overdue. */
  record InvoicePaymentFailed(String eventId, String invoiceId, String customerId) implements PaymentSignal {}

  /** Events we receive but do not act on (acknowledge with 200 so Stripe stops retrying). */
  record Ignored(String eventId, String type) implements PaymentSignal {}
}
