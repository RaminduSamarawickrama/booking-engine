package com.booking.stripe.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.booking.stripe.FakeStripeHttp;
import com.stripe.Stripe;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class StripeEventTranslatorTest {

  private static final String SECRET = "whsec_unitTestSigningSecret";

  private final FakeStripeHttp http = new FakeStripeHttp();
  private final StripeEventTranslator translator = new StripeEventTranslator(http.client(), SECRET);

  @Test
  void confirmsAPaidWebCheckout() {
    String payload = event("evt_1", "checkout.session.completed", """
        {"id":"cs_1","object":"checkout.session","payment_status":"paid","payment_intent":"pi_1",
         "metadata":{"booking_id":"bk_01","booking_reference":"AT7K2Q9M"}}""");

    List<PaymentSignal> signals = translator.translate(payload, sign(payload, now()));

    PaymentSignal.BookingPaid paid = assertInstanceOf(PaymentSignal.BookingPaid.class, signals.get(0));
    assertEquals("bk_01", paid.bookingId());
    assertEquals("pi_1", paid.paymentIntentId());
    assertEquals("web", paid.channel());
    assertEquals("evt_1", paid.eventId());
  }

  @Test
  void doesNotConfirmACheckoutStillAwaitingADelayedPayment() {
    String payload = event("evt_2", "checkout.session.completed", """
        {"id":"cs_2","object":"checkout.session","payment_status":"unpaid","payment_intent":"pi_2",
         "metadata":{"booking_id":"bk_02"}}""");

    assertInstanceOf(PaymentSignal.Ignored.class, translator.translate(payload, sign(payload, now())).get(0));
  }

  @Test
  void confirmsTheDelayedPaymentWhenItSucceeds() {
    String payload = event("evt_3", "checkout.session.async_payment_succeeded", """
        {"id":"cs_2","object":"checkout.session","payment_status":"paid","payment_intent":"pi_2",
         "metadata":{"booking_id":"bk_02","booking_reference":"QQ11"}}""");

    assertInstanceOf(PaymentSignal.BookingPaid.class, translator.translate(payload, sign(payload, now())).get(0));
  }

  @Test
  void mobilePaymentConfirmsTheBookingAndRecordsTheCharge() {
    String payload = event("evt_4", "payment_intent.succeeded", """
        {"id":"pi_9","object":"payment_intent","latest_charge":"ch_9","amount_received":15600,
         "metadata":{"booking_id":"bk_09","booking_reference":"ZZ99","channel":"mobile"}}""");

    List<PaymentSignal> signals = translator.translate(payload, sign(payload, now()));

    assertEquals(2, signals.size());
    assertEquals("mobile", assertInstanceOf(PaymentSignal.BookingPaid.class, signals.get(0)).channel());
    PaymentSignal.ChargeAvailable charge = assertInstanceOf(PaymentSignal.ChargeAvailable.class, signals.get(1));
    assertEquals("ch_9", charge.chargeId());
    assertEquals(15_600, charge.amountReceivedMinor());
  }

  @Test
  void webPaymentIntentOnlyRecordsTheChargeBecauseCheckoutConfirms() {
    String payload = event("evt_5", "payment_intent.succeeded", """
        {"id":"pi_1","object":"payment_intent","latest_charge":"ch_1","amount_received":15600,
         "metadata":{"booking_id":"bk_01","channel":"web"}}""");

    List<PaymentSignal> signals = translator.translate(payload, sign(payload, now()));

    assertEquals(1, signals.size());
    assertInstanceOf(PaymentSignal.ChargeAvailable.class, signals.get(0));
  }

  @Test
  void disputeTriggersTransferRecovery() {
    String payload = event("evt_6", "charge.dispute.created", """
        {"id":"dp_1","object":"dispute","charge":"ch_1","payment_intent":"pi_1","amount":15600,
         "reason":"fraudulent","status":"needs_response"}""");

    PaymentSignal.DisputeOpened dispute =
        assertInstanceOf(PaymentSignal.DisputeOpened.class, translator.translate(payload, sign(payload, now())).get(0));
    assertEquals("ch_1", dispute.chargeId());
    assertEquals(15_600, dispute.amountMinor());
    assertEquals("fraudulent", dispute.reason());
  }

  @Test
  void corporateInvoicePaid() {
    String payload = event("evt_7", "invoice.paid", """
        {"id":"in_1","object":"invoice","customer":"cus_corp1","amount_paid":15600}""");

    PaymentSignal.InvoicePaid paid =
        assertInstanceOf(PaymentSignal.InvoicePaid.class, translator.translate(payload, sign(payload, now())).get(0));
    assertEquals("cus_corp1", paid.customerId());
  }

  @Test
  void acknowledgesEventsWeDoNotHandle() {
    String payload = event("evt_8", "customer.created", """
        {"id":"cus_1","object":"customer"}""");
    assertEquals("customer.created",
        assertInstanceOf(PaymentSignal.Ignored.class, translator.translate(payload, sign(payload, now())).get(0)).type());
  }

  @Test
  void rejectsForgedTamperedAndReplayedRequests() {
    String payload = event("evt_9", "checkout.session.completed", """
        {"id":"cs_1","object":"checkout.session","payment_status":"paid","metadata":{"booking_id":"bk_01"}}""");

    assertThrows(StripeEventTranslator.InvalidSignatureException.class,
        () -> translator.translate(payload, "t=" + now() + ",v1=" + "0".repeat(64)));
    assertThrows(StripeEventTranslator.InvalidSignatureException.class,
        () -> translator.translate(payload.replace("bk_01", "bk_666"), sign(payload, now())));
    long tenMinutesAgo = now() - 600;
    assertThrows(StripeEventTranslator.InvalidSignatureException.class,
        () -> translator.translate(payload, sign(payload, tenMinutesAgo)));
    assertThrows(StripeEventTranslator.InvalidSignatureException.class,
        () -> translator.translate(payload, sign(payload, now(), "whsec_someoneElse")));
  }

  @Test
  void failsLoudlyWhenTheEndpointApiVersionDoesNotMatchTheSdk() {
    String payload = """
        {"id":"evt_10","object":"event","api_version":"2020-08-27","type":"checkout.session.completed",
         "data":{"object":{"id":"cs_1","object":"checkout.session","payment_status":"paid"}}}""";

    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> translator.translate(payload, sign(payload, now())));
    assertEquals(true, e.getMessage().contains("API version"));
  }

  private static String event(String id, String type, String object) {
    return "{\"id\":\"" + id + "\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
        + "\",\"type\":\"" + type + "\",\"data\":{\"object\":" + object + "}}";
  }

  private static long now() {
    return Instant.now().getEpochSecond();
  }

  private static String sign(String payload, long timestamp) {
    return sign(payload, timestamp, SECRET);
  }

  /** Same scheme Stripe uses: HMAC-SHA256 over "timestamp.payload" with the endpoint secret. */
  private static String sign(String payload, long timestamp, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
      return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
