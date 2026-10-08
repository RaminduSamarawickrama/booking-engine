package com.booking.stripe.payments;

import static com.booking.stripe.StripeClients.idempotentKey;

import com.booking.stripe.StripeGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Customer payments for bookings. The platform is the merchant of record and charges the
 * customer at booking (separate charges and transfers); drivers are paid later by
 * {@link com.booking.stripe.connect.DriverTransferGateway}.
 *
 * <p>Neither method confirms a booking. Confirmation happens only when a verified webhook
 * arrives (see {@link com.booking.stripe.webhooks.StripeEventTranslator}).
 */
public final class StripePaymentsGateway {

  /** Checkout Sessions must stay open at least 30 minutes; match the booking hold. */
  static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);

  private final StripeClient stripe;
  private final String integrationIdentifier;
  private final Clock clock;

  /**
   * @param integrationIdentifier label for this checkout flow in the Dashboard, ending in
   *     8 random letters (e.g. "booking-web-qkzvhtra")
   */
  public StripePaymentsGateway(StripeClient stripe, String integrationIdentifier, Clock clock) {
    this.stripe = Objects.requireNonNull(stripe);
    if (integrationIdentifier == null || !integrationIdentifier.matches("^[a-z0-9-]+-[a-z]{8}$")) {
      throw new IllegalArgumentException("integrationIdentifier must end with -<8 random letters>");
    }
    this.integrationIdentifier = integrationIdentifier;
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Web checkout: a Checkout Session in {@code elements} UI mode, rendered on our own page with
   * the Payment Element. Returns the client secret the browser needs.
   *
   * @param returnUrl page the customer returns to after any redirect-based payment method;
   *     must contain {CHECKOUT_SESSION_ID}
   */
  public CheckoutSessionResult createWebCheckout(BookingPayment payment, String returnUrl) {
    if (returnUrl == null || !returnUrl.contains("{CHECKOUT_SESSION_ID}")) {
      throw new IllegalArgumentException("returnUrl must contain {CHECKOUT_SESSION_ID}");
    }
    SessionCreateParams.Builder params =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setUiMode(SessionCreateParams.UiMode.ELEMENTS)
            .setReturnUrl(returnUrl)
            .setClientReferenceId(payment.bookingId())
            .setExpiresAt(clock.instant().plus(SESSION_LIFETIME).getEpochSecond())
            .setIntegrationIdentifier(integrationIdentifier)
            .putMetadata("booking_id", payment.bookingId())
            .putMetadata("booking_reference", payment.bookingReference())
            .setPaymentIntentData(
                SessionCreateParams.PaymentIntentData.builder()
                    .setTransferGroup(payment.transferGroup())
                    .setDescription("Airport transfer " + payment.bookingReference())
                    .putMetadata("booking_id", payment.bookingId())
                    .putMetadata("booking_reference", payment.bookingReference())
                    .putMetadata("channel", "web")
                    .build());
    if (payment.customerEmail() != null) {
      params.setCustomerEmail(payment.customerEmail());
    }
    for (BookingPayment.Leg leg : payment.legs()) {
      params.addLineItem(
          SessionCreateParams.LineItem.builder()
              .setQuantity(1L)
              .setPriceData(
                  SessionCreateParams.LineItem.PriceData.builder()
                      .setCurrency(payment.currency())
                      .setUnitAmount(leg.amountMinor())
                      .setProductData(
                          SessionCreateParams.LineItem.PriceData.ProductData.builder()
                              .setName(leg.description())
                              .putMetadata("leg_id", leg.legId())
                              .build())
                      .build())
              .build());
    }
    try {
      Session session =
          stripe.v1().checkout().sessions().create(
              params.build(), idempotentKey("checkout:" + payment.bookingId() + ":" + payment.attempt()));
      return new CheckoutSessionResult(session.getId(), session.getClientSecret());
    } catch (StripeException e) {
      throw new StripeGatewayException("Create Checkout Session for " + payment.bookingReference(), e);
    }
  }

  /**
   * Mobile checkout: a PaymentIntent for the React Native PaymentSheet. Payment methods are
   * dynamic (configured in the Dashboard); no payment_method_types are passed.
   */
  public PaymentIntentResult createMobilePaymentIntent(BookingPayment payment) {
    PaymentIntentCreateParams.Builder params =
        PaymentIntentCreateParams.builder()
            .setAmount(payment.totalMinor())
            .setCurrency(payment.currency())
            .setTransferGroup(payment.transferGroup())
            .setDescription("Airport transfer " + payment.bookingReference())
            .putMetadata("booking_id", payment.bookingId())
            .putMetadata("booking_reference", payment.bookingReference())
            .putMetadata("channel", "mobile");
    if (payment.customerEmail() != null) {
      params.setReceiptEmail(payment.customerEmail());
    }
    try {
      PaymentIntent intent =
          stripe.v1().paymentIntents().create(
              params.build(), idempotentKey("payment-intent:" + payment.bookingId() + ":" + payment.attempt()));
      return new PaymentIntentResult(intent.getId(), intent.getClientSecret());
    } catch (StripeException e) {
      throw new StripeGatewayException("Create PaymentIntent for " + payment.bookingReference(), e);
    }
  }

  /**
   * Refunds all or part of a booking payment. Reversing driver transfers for legs that were
   * already paid out is a separate step ({@code DriverTransferGateway.reverse}).
   *
   * @param amountMinor null refunds the remaining amount
   * @param refundRequestId our own ID for this refund decision (idempotency)
   */
  public String refund(String paymentIntentId, Long amountMinor, String refundRequestId, String reason) {
    RefundCreateParams.Builder params =
        RefundCreateParams.builder()
            .setPaymentIntent(paymentIntentId)
            .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
            .putMetadata("refund_request_id", refundRequestId);
    if (amountMinor != null) {
      if (amountMinor <= 0) throw new IllegalArgumentException("refund amount must be positive");
      params.setAmount(amountMinor);
    }
    if (reason != null) {
      params.putMetadata("reason", reason.length() > 450 ? reason.substring(0, 450) : reason);
    }
    try {
      Refund refund = stripe.v1().refunds().create(params.build(), idempotentKey("refund:" + refundRequestId));
      return refund.getId();
    } catch (StripeException e) {
      throw new StripeGatewayException("Refund " + paymentIntentId, e);
    }
  }

  public record CheckoutSessionResult(String sessionId, String clientSecret) {}

  public record PaymentIntentResult(String paymentIntentId, String clientSecret) {}
}
