package com.booking.stripe.connect;

import static com.booking.stripe.FakeStripeHttp.form;
import static com.booking.stripe.FakeStripeHttp.idempotencyKey;
import static com.booking.stripe.FakeStripeHttp.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.booking.stripe.FakeStripeHttp;
import com.stripe.net.StripeRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DriverTransferGatewayTest {

  private final FakeStripeHttp http = new FakeStripeHttp();
  private final DriverTransferGateway gateway = new DriverTransferGateway(http.client(), new CommissionPolicy(2500));

  private final DriverTransferGateway.RidePayout outboundLeg =
      new DriverTransferGateway.RidePayout("ride_out", "AT7K2Q9M", "acct_drv1", "ch_3Abc", 8_000, "gbp");

  @Test
  void transfersTheDriversShareFromTheBookingCharge() {
    http.reply("{\"id\":\"tr_1\",\"object\":\"transfer\"}");

    DriverTransferGateway.DriverTransferResult result = gateway.payDriverForRide(outboundLeg);

    assertEquals("tr_1", result.transferId());
    assertEquals(6_000, result.driverAmountMinor()); // 75% of £80
    assertEquals(2_000, result.platformAmountMinor());
    StripeRequest request = http.only();
    assertEquals("/v1/transfers", path(request));
    assertEquals("transfer:ride:ride_out", idempotencyKey(request));
    Map<String, String> p = form(request);
    assertEquals("6000", p.get("amount"));
    assertEquals("acct_drv1", p.get("destination"));
    assertEquals("ch_3Abc", p.get("source_transaction"));
    assertEquals("booking_AT7K2Q9M", p.get("transfer_group"));
    assertEquals("2500", p.get("metadata[commission_bps]"));
    assertFalse(p.containsKey("application_fee_amount"), "not compatible with separate charges and transfers");
  }

  @Test
  void payingTheSameRideTwiceReusesTheIdempotencyKey() {
    http.reply("{\"id\":\"tr_1\",\"object\":\"transfer\"}").reply("{\"id\":\"tr_1\",\"object\":\"transfer\"}");
    gateway.payDriverForRide(outboundLeg);
    gateway.payDriverForRide(outboundLeg);
    assertEquals(idempotencyKey(http.requests().get(0)), idempotencyKey(http.requests().get(1)));
  }

  @Test
  void reversesATransferAfterADispute() {
    http.reply("{\"id\":\"trr_1\",\"object\":\"transfer_reversal\"}");

    String reversalId = gateway.reverse("tr_1", null, "dp_55");

    assertEquals("trr_1", reversalId);
    StripeRequest request = http.only();
    assertEquals("/v1/transfers/tr_1/reversals", path(request));
    assertEquals("reversal:tr_1:dp_55", idempotencyKey(request));
    assertFalse(form(request).containsKey("amount"), "full reversal");
    assertEquals("dp_55", form(request).get("metadata[reason_key]"));
  }

  @Test
  void rejectsPaymentIntentIdsAsTheSourceTransaction() {
    assertThrows(IllegalArgumentException.class,
        () -> new DriverTransferGateway.RidePayout("ride", "REF", "acct_1", "pi_123", 100, "gbp"));
    assertThrows(IllegalArgumentException.class,
        () -> new DriverTransferGateway.RidePayout("ride", "REF", "cus_1", "ch_123", 100, "gbp"));
  }
}
