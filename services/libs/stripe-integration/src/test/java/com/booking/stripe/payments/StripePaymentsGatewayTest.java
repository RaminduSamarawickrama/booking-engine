package com.booking.stripe.payments;

import static com.booking.stripe.FakeStripeHttp.form;
import static com.booking.stripe.FakeStripeHttp.idempotencyKey;
import static com.booking.stripe.FakeStripeHttp.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.booking.stripe.FakeStripeHttp;
import com.stripe.net.StripeRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripePaymentsGatewayTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

  private final FakeStripeHttp http = new FakeStripeHttp();
  private final StripePaymentsGateway gateway =
      new StripePaymentsGateway(http.client(), "booking-web-qkzvhtra", Clock.fixed(NOW, ZoneOffset.UTC));

  private final BookingPayment returnTrip =
      new BookingPayment(
          "bk_01", "AT7K2Q9M", 1, "gbp", "traveller@example.com",
          List.of(
              new BookingPayment.Leg("ride_out", "Heathrow T5 to SW1A 1AA, 14 Oct 09:30", 8_000),
              new BookingPayment.Leg("ride_ret", "SW1A 1AA to Heathrow T5, 21 Oct 06:00", 7_600)));

  @Test
  void webCheckoutUsesElementsModeAndGroupsTheBookingForTransfers() {
    http.reply("{\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"client_secret\":\"cs_test_1_secret_x\"}");

    StripePaymentsGateway.CheckoutSessionResult result =
        gateway.createWebCheckout(returnTrip, "https://booking-customer-web.vercel.app/return?session_id={CHECKOUT_SESSION_ID}");

    assertEquals("cs_test_1", result.sessionId());
    assertEquals("cs_test_1_secret_x", result.clientSecret());
    StripeRequest request = http.only();
    assertEquals("/v1/checkout/sessions", path(request));
    assertEquals("checkout:bk_01:1", idempotencyKey(request));

    Map<String, String> p = form(request);
    assertEquals("payment", p.get("mode"));
    assertEquals("elements", p.get("ui_mode"));
    assertEquals("booking-web-qkzvhtra", p.get("integration_identifier"));
    assertEquals("booking_AT7K2Q9M", p.get("payment_intent_data[transfer_group]"));
    assertEquals("bk_01", p.get("payment_intent_data[metadata][booking_id]"));
    assertEquals("web", p.get("payment_intent_data[metadata][channel]"));
    assertEquals("bk_01", p.get("client_reference_id"));
    assertEquals(Long.toString(NOW.plusSeconds(1800).getEpochSecond()), p.get("expires_at"));
    assertEquals("8000", p.get("line_items[0][price_data][unit_amount]"));
    assertEquals("7600", p.get("line_items[1][price_data][unit_amount]"));
    assertEquals("ride_ret", p.get("line_items[1][price_data][product_data][metadata][leg_id]"));
    assertEquals("traveller@example.com", p.get("customer_email"));
  }

  @Test
  void neverRestrictsPaymentMethodTypes() {
    http.reply("{\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"client_secret\":\"s\"}");
    gateway.createWebCheckout(returnTrip, "https://x.example/return?session_id={CHECKOUT_SESSION_ID}");
    http.reply("{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"client_secret\":\"s\"}");
    gateway.createMobilePaymentIntent(returnTrip);

    for (StripeRequest request : http.requests()) {
      assertFalse(
          form(request).keySet().stream().anyMatch(k -> k.startsWith("payment_method_types")),
          "payment_method_types must not be sent (dynamic payment methods)");
    }
  }

  @Test
  void mobilePaymentIntentChargesTheBookingTotal() {
    http.reply("{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"client_secret\":\"pi_1_secret_y\"}");

    StripePaymentsGateway.PaymentIntentResult result = gateway.createMobilePaymentIntent(returnTrip);

    assertEquals("pi_1_secret_y", result.clientSecret());
    StripeRequest request = http.only();
    assertEquals("/v1/payment_intents", path(request));
    assertEquals("payment-intent:bk_01:1", idempotencyKey(request));
    Map<String, String> p = form(request);
    assertEquals("15600", p.get("amount"));
    assertEquals("gbp", p.get("currency"));
    assertEquals("booking_AT7K2Q9M", p.get("transfer_group"));
    assertEquals("mobile", p.get("metadata[channel]"));
    assertEquals("traveller@example.com", p.get("receipt_email"));
  }

  @Test
  void aRetryAfterFailureGetsANewIdempotencyKey() {
    BookingPayment secondAttempt =
        new BookingPayment("bk_01", "AT7K2Q9M", 2, "gbp", null, returnTrip.legs());
    http.reply("{\"id\":\"pi_2\",\"object\":\"payment_intent\",\"client_secret\":\"s\"}");
    gateway.createMobilePaymentIntent(secondAttempt);
    assertEquals("payment-intent:bk_01:2", idempotencyKey(http.only()));
    assertFalse(form(http.only()).containsKey("receipt_email"));
  }

  @Test
  void partialRefundIsIdempotentPerRefundDecision() {
    http.reply("{\"id\":\"re_1\",\"object\":\"refund\"}");

    String refundId = gateway.refund("pi_1", 7_600L, "rfq_44", "Return leg cancelled 48h ahead");

    assertEquals("re_1", refundId);
    StripeRequest request = http.only();
    assertEquals("/v1/refunds", path(request));
    assertEquals("refund:rfq_44", idempotencyKey(request));
    Map<String, String> p = form(request);
    assertEquals("pi_1", p.get("payment_intent"));
    assertEquals("7600", p.get("amount"));
    assertEquals("requested_by_customer", p.get("reason"));
  }

  @Test
  void validatesInputsBeforeCallingStripe() {
    assertThrows(IllegalArgumentException.class, () -> gateway.createWebCheckout(returnTrip, "https://x.example/return"));
    assertThrows(IllegalArgumentException.class, () -> gateway.refund("pi_1", 0L, "rfq", null));
    assertThrows(IllegalArgumentException.class,
        () -> new StripePaymentsGateway(http.client(), "no-random-suffix", Clock.systemUTC()));
    assertThrows(IllegalArgumentException.class,
        () -> new BookingPayment("bk", "REF", 1, "GBP", null, returnTrip.legs()));
    assertTrue(http.requests().isEmpty());
  }
}
