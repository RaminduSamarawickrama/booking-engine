package com.booking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import com.booking.payment.client.BookingClient;
import com.booking.payment.service.PaymentService;
import com.booking.platform.test.Infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = { "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "booking.platform.messaging.relay-initial-delay=PT1H", "management.server.port=" })
@AutoConfigureMockMvc
@Import(Infrastructure.class)
class MockPaymentFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired PaymentService payments;

    @MockitoBean BookingClient bookings;

    static final String TOKEN = "guest-manage-token";

    private String reference;
    private UUID bookingId;

    @BeforeEach
    void aPendingBooking() {
        bookingId = UUID.randomUUID();
        reference = "TR" + bookingId.toString().substring(0, 6).toUpperCase();
        when(bookings.find(any(), any(), any())).thenReturn(Optional.empty());
        when(bookings.find(eq(reference), eq(TOKEN), any())).thenReturn(Optional.of(
                new BookingClient.BookingSummary(bookingId, reference, "PENDING_PAYMENT", 9_000, "GBP", "grace@example.test")));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions start(String token) throws Exception {
        var request = post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingReference\":\"" + reference + "\"}");
        return mvc.perform(token == null ? request : request.header("X-Booking-Token", token));
    }

    private ResultActions card(String paymentId, String number) throws Exception {
        return mvc.perform(post("/v1/payments/" + paymentId + "/mock-confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardNumber\":\"" + number + "\"}"));
    }

    private int events(String type) {
        return jdbc.sql("select count(*) from outbox_event where event_type = :t and payload->>'bookingId' = :b")
                .param("t", type).param("b", bookingId.toString()).query(Integer.class).single();
    }

    @Test
    void aTestCardPaysTheBookingAndPublishesOneSuccess() throws Exception {
        JsonNode started = read(start(TOKEN).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amountMinor").value(9_000))
                .andExpect(jsonPath("$.provider").value("mock")));
        String id = started.get("paymentId").asString();

        card(id, "4242 4242 4242 4242").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        card(id, "4242 4242 4242 4242").andExpect(status().isConflict());
        mvc.perform(get("/v1/payments/" + id)).andExpect(jsonPath("$.status").value("SUCCEEDED"));

        assertThat(events("payment.succeeded")).isEqualTo(1);
        start(TOKEN).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already_paid"));
    }

    @Test
    void aDeclinedCardCanBeRetried() throws Exception {
        String first = read(start(TOKEN)).get("paymentId").asString();
        card(first, "4000 0000 0000 0002").andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("card_declined"));
        assertThat(events("payment.failed")).isEqualTo(1);

        String second = read(start(TOKEN).andExpect(status().isCreated())).get("paymentId").asString();
        assertThat(second).isNotEqualTo(first);
        card(second, "4242424242424242").andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void onlyPeopleWhoCanSeeTheBookingCanPayForIt() throws Exception {
        start(null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("booking_not_found"));
        start("wrong-token").andExpect(status().isNotFound());
    }

    @Test
    void expiredBookingsCannotBePaid() throws Exception {
        when(bookings.find(eq(reference), eq(TOKEN), any())).thenReturn(Optional.of(
                new BookingClient.BookingSummary(bookingId, reference, "EXPIRED", 9_000, "GBP", null)));
        start(TOKEN).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("booking_not_payable"));
    }

    @Test
    void aSecondSuccessfulPaymentIsRefunded() throws Exception {
        String first = read(start(TOKEN)).get("paymentId").asString();
        String second = read(start(TOKEN)).get("paymentId").asString();
        card(first, "4242424242424242").andExpect(jsonPath("$.status").value("SUCCEEDED"));
        card(second, "4242424242424242").andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.failureCode").value("duplicate_payment"));
        assertThat(events("payment.succeeded")).isEqualTo(1);
        assertThat(events("payment.refunded")).isEqualTo(1);
    }

    @Test
    void aPaymentTheBookingRejectsIsRefundedOnce() throws Exception {
        String id = read(start(TOKEN)).get("paymentId").asString();
        card(id, "4242424242424242");
        UUID eventId = UUID.randomUUID();

        payments.bookingRejectedPayment(eventId, "test", UUID.fromString(id), "booking is expired");
        payments.bookingRejectedPayment(eventId, "test", UUID.fromString(id), "booking is expired");

        mvc.perform(get("/v1/payments/" + id)).andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(events("payment.refunded")).isEqualTo(1);
    }

    @Test
    void stripeWebhooksAreRefusedWhileStripeIsOff() throws Exception {
        mvc.perform(post("/v1/payments/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
