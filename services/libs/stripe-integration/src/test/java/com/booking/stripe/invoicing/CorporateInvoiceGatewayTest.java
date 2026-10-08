package com.booking.stripe.invoicing;

import static com.booking.stripe.FakeStripeHttp.form;
import static com.booking.stripe.FakeStripeHttp.idempotencyKey;
import static com.booking.stripe.FakeStripeHttp.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.booking.stripe.FakeStripeHttp;
import com.stripe.net.StripeRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorporateInvoiceGatewayTest {

  private final FakeStripeHttp http = new FakeStripeHttp();
  private final CorporateInvoiceGateway gateway = new CorporateInvoiceGateway(http.client());

  @Test
  void issuesAMonthlyInvoiceOnTermsWithOneLinePerRide() {
    http.reply("{\"id\":\"in_1\",\"object\":\"invoice\",\"status\":\"draft\"}")
        .reply("{\"id\":\"ii_1\",\"object\":\"invoiceitem\"}")
        .reply("{\"id\":\"ii_2\",\"object\":\"invoiceitem\"}")
        .reply("{\"id\":\"in_1\",\"object\":\"invoice\",\"status\":\"open\"}")
        .reply("{\"id\":\"in_1\",\"object\":\"invoice\",\"status\":\"open\",\"number\":\"AB12-0001\","
            + "\"hosted_invoice_url\":\"https://invoice.stripe.com/i/x\",\"amount_due\":15600}");

    CorporateInvoiceGateway.IssuedInvoice invoice = gateway.issueMonthlyInvoice(
        "cus_corp1", "2026-10", "gbp", 30,
        List.of(
            new CorporateInvoiceGateway.InvoiceLine("ride_1", "AT7K2Q9M", "Heathrow T5 to SW1A 1AA, 14 Oct", 8_000),
            new CorporateInvoiceGateway.InvoiceLine("ride_2", "AT7K2Q9M", "SW1A 1AA to Heathrow T5, 21 Oct", 7_600)));

    assertEquals("AB12-0001", invoice.number());
    assertEquals(15_600L, invoice.amountDueMinor());
    List<StripeRequest> calls = http.requests();
    assertEquals(5, calls.size());

    Map<String, String> create = form(calls.get(0));
    assertEquals("/v1/invoices", path(calls.get(0)));
    assertEquals("send_invoice", create.get("collection_method"));
    assertEquals("30", create.get("days_until_due"));
    assertEquals("exclude", create.get("pending_invoice_items_behavior"));
    assertFalse(create.keySet().stream().anyMatch(k -> k.startsWith("transfer_data")),
        "platform is merchant of record; drivers are paid by separate transfers");

    assertEquals("/v1/invoiceitems", path(calls.get(1)));
    assertEquals("in_1", form(calls.get(1)).get("invoice"));
    assertEquals("ride_2", form(calls.get(2)).get("metadata[ride_id]"));
    assertEquals("/v1/invoices/in_1/finalize", path(calls.get(3)));
    assertEquals("/v1/invoices/in_1/send", path(calls.get(4)));

    assertEquals("invoice:cus_corp1:2026-10:create", idempotencyKey(calls.get(0)));
    assertEquals("invoice:cus_corp1:2026-10:line:ride_1", idempotencyKey(calls.get(1)));
    assertEquals("invoice:cus_corp1:2026-10:send", idempotencyKey(calls.get(4)));
  }

  @Test
  void refusesEmptyInvoicesAndOddTerms() {
    assertThrows(IllegalArgumentException.class, () -> gateway.issueMonthlyInvoice("cus", "2026-10", "gbp", 30, List.of()));
    assertThrows(IllegalArgumentException.class, () -> gateway.issueMonthlyInvoice("cus", "2026-10", "gbp", 0,
        List.of(new CorporateInvoiceGateway.InvoiceLine("r", "B", "d", 100))));
  }
}
