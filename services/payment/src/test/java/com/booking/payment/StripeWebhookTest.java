package com.booking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.booking.platform.test.Infrastructure;
import com.stripe.Stripe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Stripe mode without network: only signed webhooks are exercised (no API calls). */
@SpringBootTest(properties = { "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "booking.platform.messaging.relay-initial-delay=PT1H", "management.server.port=",
        "PAYMENT_PROVIDER=stripe", "STRIPE_API_KEY=rk_test_unitTestOnly", "STRIPE_WEBHOOK_SECRET=" + StripeWebhookTest.SECRET,
        "STRIPE_PUBLISHABLE_KEY=pk_test_unitTestOnly" })
@AutoConfigureMockMvc
@Import(Infrastructure.class)
class StripeWebhookTest {

    static final String SECRET = "whsec_unitTestSigningSecret";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    private UUID pendingStripePayment() {
        UUID payment = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        jdbc.sql("""
                insert into payment (id, booking_id, booking_reference, attempt, amount_minor, currency, provider,
                                     provider_reference, status, created_at, updated_at)
                values (:id, :b, 'TRSTRIPE', 1, 9000, 'GBP', 'stripe', 'cs_test_1', 'PENDING', :at, :at)
                """).param("id", payment).param("b", booking).param("at", Timestamp.from(Instant.now())).update();
        return booking;
    }

    private static String event(String id, String type, String object) {
        return "{\"id\":\"" + id + "\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
                + "\",\"type\":\"" + type + "\",\"data\":{\"object\":" + object + "}}";
    }

    private static String sign(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1="
                + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aSignedCheckoutCompletionPaysTheBookingOnce() throws Exception {
        UUID booking = pendingStripePayment();
        String payload = event("evt_paid_" + booking, "checkout.session.completed", """
                {"id":"cs_test_1","object":"checkout.session","payment_status":"paid","payment_intent":"pi_test_1",
                 "metadata":{"booking_id":"%s","booking_reference":"TRSTRIPE"}}""".formatted(booking));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/v1/payments/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", sign(payload)).content(payload)).andExpect(status().isOk());
        }

        assertThat(jdbc.sql("select status from payment where booking_id = :b").param("b", booking)
                .query(String.class).single()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.sql("select count(*) from outbox_event where event_type = 'payment.succeeded' and payload->>'bookingId' = :b")
                .param("b", booking.toString()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void unsignedOrTamperedWebhooksAreRejected() throws Exception {
        UUID booking = pendingStripePayment();
        String payload = event("evt_forged", "checkout.session.completed", """
                {"id":"cs_test_1","object":"checkout.session","payment_status":"paid","payment_intent":"pi_x",
                 "metadata":{"booking_id":"%s","booking_reference":"TRSTRIPE"}}""".formatted(booking));
        mvc.perform(post("/v1/payments/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/payments/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", sign(payload)).content(payload.replace("paid\"", "paid \"")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.sql("select status from payment where booking_id = :b").param("b", booking)
                .query(String.class).single()).isEqualTo("PENDING");
    }
}
