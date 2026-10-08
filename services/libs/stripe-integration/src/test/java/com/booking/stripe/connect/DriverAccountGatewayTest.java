package com.booking.stripe.connect;

import static com.booking.stripe.FakeStripeHttp.body;
import static com.booking.stripe.FakeStripeHttp.idempotencyKey;
import static com.booking.stripe.FakeStripeHttp.path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.booking.stripe.FakeStripeHttp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.net.StripeRequest;
import org.junit.jupiter.api.Test;

class DriverAccountGatewayTest {

  private final FakeStripeHttp http = new FakeStripeHttp();
  private final DriverAccountGateway gateway = new DriverAccountGateway(http.client());

  @Test
  void createsAnAccountsV2RecipientWithPlatformResponsibilities() {
    http.reply("{\"id\":\"acct_drv1\",\"object\":\"v2.core.account\"}");

    String accountId = gateway.createDriverAccount(
        new DriverAccountGateway.DriverAccountRequest("drv_9", "Sam Driver", "sam@example.com", "gb", "gbp"));

    assertEquals("acct_drv1", accountId);
    StripeRequest request = http.only();
    assertEquals("/v2/core/accounts", path(request));
    assertEquals("driver-account:drv_9", idempotencyKey(request));

    JsonObject json = JsonParser.parseString(body(request)).getAsJsonObject();
    assertEquals("express", json.get("dashboard").getAsString());
    JsonObject responsibilities = json.getAsJsonObject("defaults").getAsJsonObject("responsibilities");
    assertEquals("application", responsibilities.get("fees_collector").getAsString());
    assertEquals("application", responsibilities.get("losses_collector").getAsString());
    assertEquals("gbp", json.getAsJsonObject("defaults").get("currency").getAsString());
    assertEquals("gb", json.getAsJsonObject("identity").get("country").getAsString());
    assertEquals("individual", json.getAsJsonObject("identity").get("entity_type").getAsString());

    JsonObject configuration = json.getAsJsonObject("configuration");
    assertTrue(configuration.getAsJsonObject("recipient")
        .getAsJsonObject("capabilities").getAsJsonObject("stripe_balance")
        .getAsJsonObject("stripe_transfers").get("requested").getAsBoolean());
    assertFalse(configuration.has("merchant"), "drivers never take payments directly");
    assertFalse(body(request).contains("card_payments"));
    assertFalse(json.has("type"), "legacy account types are not used");
  }

  @Test
  void onboardingLinkTargetsTheRecipientConfiguration() {
    http.reply("{\"id\":\"accountlink_1\",\"object\":\"v2.core.account_link\",\"url\":\"https://connect.stripe.com/setup/x\"}");

    String url = gateway.createOnboardingLink(
        "acct_drv1", "https://api.example/v1/driver/payouts/refresh", "https://api.example/v1/driver/payouts/return");

    assertEquals("https://connect.stripe.com/setup/x", url);
    StripeRequest request = http.only();
    assertEquals("/v2/core/account_links", path(request));
    JsonObject json = JsonParser.parseString(body(request)).getAsJsonObject();
    assertEquals("acct_drv1", json.get("account").getAsString());
    JsonObject useCase = json.getAsJsonObject("use_case");
    assertEquals("account_onboarding", useCase.get("type").getAsString());
    assertEquals("recipient",
        useCase.getAsJsonObject("account_onboarding").getAsJsonArray("configurations").get(0).getAsString());
  }

  @Test
  void loginLinkOpensTheExpressDashboard() {
    http.reply("{\"object\":\"login_link\",\"url\":\"https://connect.stripe.com/express/x\"}");
    assertEquals("https://connect.stripe.com/express/x", gateway.createDashboardLoginLink("acct_drv1"));
    assertEquals("/v1/accounts/acct_drv1/login_links", path(http.only()));
  }

  @Test
  void readinessComesFromV2CapabilityStatus() {
    http.reply("""
        {"id":"acct_drv1","object":"v2.core.account",
         "configuration":{"recipient":{"capabilities":{"stripe_balance":{
             "stripe_transfers":{"status":"active"},
             "payouts":{"status":"pending"}}}}},
         "requirements":{"entries":[]}}
        """);

    DriverAccountGateway.PayoutReadiness readiness = gateway.readiness("acct_drv1");

    assertTrue(readiness.canReceiveTransfers());
    assertEquals("pending", readiness.payoutsStatus());
    assertFalse(readiness.requirementsOutstanding());
    StripeRequest request = http.only();
    assertEquals("/v2/core/accounts/acct_drv1", path(request));
    String query = request.url().getQuery();
    assertTrue(query.contains("include=configuration.recipient") || query.contains("include%5B0%5D=configuration.recipient")
        || query.contains("configuration.recipient"), query);
  }

  @Test
  void accountWithoutActiveTransfersIsNotReady() {
    http.reply("""
        {"id":"acct_drv2","object":"v2.core.account",
         "configuration":{"recipient":{"capabilities":{"stripe_balance":{
             "stripe_transfers":{"status":"restricted"}}}}},
         "requirements":{"entries":[{"description":"Provide a bank account"}]}}
        """);

    DriverAccountGateway.PayoutReadiness readiness = gateway.readiness("acct_drv2");

    assertFalse(readiness.canReceiveTransfers());
    assertEquals("unrequested", readiness.payoutsStatus());
    assertTrue(readiness.requirementsOutstanding());
  }
}
