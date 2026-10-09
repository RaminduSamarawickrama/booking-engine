package com.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.booking.booking.client.CatalogClient;
import com.booking.booking.service.BookingService;
import com.booking.booking.client.PricingClient;
import com.booking.booking.repository.BookingRepository;
import com.booking.booking.domain.BookingStatus;
import com.booking.platform.security.ResourceServerSecurity;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = { "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "booking.platform.messaging.relay-initial-delay=PT1H", "management.server.port=" })
@AutoConfigureMockMvc
@Import(Infrastructure.class)
class BookingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired BookingService bookings;
    @Autowired BookingRepository repository;

    @MockitoBean PricingClient pricing;
    @MockitoBean CatalogClient catalog;

    static final UUID CITY_QUOTE = UUID.randomUUID();
    static final UUID AIRPORT_QUOTE = UUID.randomUUID();
    static final UUID EXPIRED_QUOTE = UUID.randomUUID();

    private PricingClient.Quote quote(UUID id, boolean fromAirport, Instant expiresAt) {
        JsonNode airport = json.readTree("""
                {"id":"LHR-T5","name":"Heathrow Terminal 5","address":"Heathrow","latitude":51.47,"longitude":-0.48,
                 "airportIata":"LHR","terminal":"T5"}""");
        JsonNode city = json.readTree("""
                {"id":"the-shard","name":"The Shard","address":"London SE1","latitude":51.50,"longitude":-0.08,
                 "airportIata":null,"terminal":null}""");
        return new PricingClient.Quote(id, expiresAt, "GBP",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).truncatedTo(ChronoUnit.MINUTES),
                fromAirport ? airport : city, fromAirport ? city : airport, 2, 2, 31_000, 3_000,
                List.of(new PricingClient.Option("STANDARD", 6_500), new PricingClient.Option("EXECUTIVE", 9_800)),
                List.of(new PricingClient.ExtraPrice("CHILD_SEAT", 800), new PricingClient.ExtraPrice("MEET_GREET", 1_200)));
    }

    @BeforeEach
    void stubs() {
        Instant later = Instant.now().plus(30, ChronoUnit.MINUTES);
        when(pricing.quote(any())).thenReturn(Optional.empty());
        when(pricing.quote(CITY_QUOTE)).thenReturn(Optional.of(quote(CITY_QUOTE, false, later)));
        when(pricing.quote(AIRPORT_QUOTE)).thenReturn(Optional.of(quote(AIRPORT_QUOTE, true, later)));
        when(pricing.quote(EXPIRED_QUOTE)).thenReturn(Optional.of(quote(EXPIRED_QUOTE, false, Instant.now().minusSeconds(3600))));
        when(catalog.extras()).thenReturn(Map.of(
                "CHILD_SEAT", new CatalogClient.Extra("CHILD_SEAT", "Child seat", 3),
                "MEET_GREET", new CatalogClient.Extra("MEET_GREET", "Meet and greet", 1)));
    }

    private static String body(UUID quoteId, String extras, String flight) {
        return """
                {"quoteId":"%s","categoryCode":"STANDARD","extras":%s,
                 "customerName":"Grace Hopper","customerEmail":"Grace@Example.test","customerPhone":"+44 7700 900123",
                 "flightNumber":%s,"driverNotes":"Two large cases"}
                """.formatted(quoteId, extras, flight == null ? "null" : "\"" + flight + "\"");
    }

    private ResultActions create(String content, RequestPostProcessor... with) throws Exception {
        MockHttpServletRequestBuilder request = post("/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(content);
        for (RequestPostProcessor p : with) {
            request = request.with(p);
        }
        return mvc.perform(request);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor user(UUID id, String... roles) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("roles", List.of(roles.length == 0 ? new String[] {"CUSTOMER"} : roles)))
                .authorities(ResourceServerSecurity.rolesConverter());
    }

    @Test
    void aGuestBooksWithoutAnAccountAndManagesItWithTheLink() throws Exception {
        JsonNode created = read(create(body(CITY_QUOTE, "[{\"code\":\"CHILD_SEAT\",\"quantity\":2}]", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.booking.customerEmail").value("grace@example.test"))
                .andExpect(jsonPath("$.booking.vehiclePriceMinor").value(6_500))
                .andExpect(jsonPath("$.booking.extrasTotalMinor").value(1_600))
                .andExpect(jsonPath("$.booking.totalMinor").value(8_100))
                .andExpect(jsonPath("$.booking.linkedToAccount").value(false)));
        String reference = created.get("booking").get("reference").asString();
        String token = created.get("manageToken").asString();
        assertThat(reference).matches("TR[23456789A-HJKMNP-Z]{6}");

        mvc.perform(get("/v1/bookings/" + reference.toLowerCase()).header("X-Booking-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference));
        mvc.perform(get("/v1/bookings/" + reference)).andExpect(status().isNotFound());
        mvc.perform(get("/v1/bookings/" + reference).header("X-Booking-Token", "guessed-token"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/bookings/" + reference).with(user(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/bookings/" + reference).with(user(UUID.randomUUID(), "DISPATCHER")))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/bookings/" + reference + "/cancel").header("X-Booking-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/v1/bookings/" + reference + "/cancel").header("X-Booking-Token", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cannot_cancel"));

        Integer events = jdbc.sql("select count(*) from outbox_event where aggregate_id = (select id::text from booking where reference = :r)")
                .param("r", reference).query(Integer.class).single();
        assertThat(events).isEqualTo(2); // booking.created, booking.cancelled
    }

    @Test
    void retriesWithTheSameIdempotencyKeyReturnTheFirstBooking() throws Exception {
        String key = "retry-" + UUID.randomUUID();
        String content = body(CITY_QUOTE, "[]", null);
        JsonNode first = read(mvc.perform(post("/v1/bookings").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isCreated()));
        JsonNode retry = read(mvc.perform(post("/v1/bookings").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isOk()));
        assertThat(retry.get("booking").get("reference").asString())
                .isEqualTo(first.get("booking").get("reference").asString());
        assertThat(retry.get("manageToken").asString()).isEqualTo(first.get("manageToken").asString());

        mvc.perform(post("/v1/bookings").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(body(CITY_QUOTE, "[{\"code\":\"MEET_GREET\",\"quantity\":1}]", null)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));
    }

    @Test
    void airportPickupsNeedAValidFlightNumber() throws Exception {
        create(body(AIRPORT_QUOTE, "[]", null)).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("flight_number_required"));
        create(body(AIRPORT_QUOTE, "[]", "not a flight")).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("invalid_flight_number"));
        create(body(AIRPORT_QUOTE, "[]", "ba 117")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.flightNumber").value("BA117"));
    }

    @Test
    void pricesComeOnlyFromAValidQuote() throws Exception {
        create(body(EXPIRED_QUOTE, "[]", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote_expired"));
        create(body(UUID.randomUUID(), "[]", null)).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("quote_not_found"));
        create(body(CITY_QUOTE, "[]", null).replace("STANDARD", "MINIBUS")).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("vehicle_not_offered"));
        create(body(CITY_QUOTE, "[{\"code\":\"MEET_GREET\",\"quantity\":2}]", null)).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("too_many_extras"));
        create(body(CITY_QUOTE, "[{\"code\":\"JACUZZI\",\"quantity\":1}]", null)).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("unknown_extra"));
        create(body(CITY_QUOTE, "[]", null).replace("+44 7700 900123", "call me")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void signedInCustomersSeeTheirBookingsAndCanSaveAGuestBooking() throws Exception {
        UUID ada = UUID.randomUUID();
        create(body(CITY_QUOTE, "[]", null), user(ada)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.linkedToAccount").value(true));

        JsonNode guest = read(create(body(CITY_QUOTE, "[]", null)).andExpect(status().isCreated()));
        String reference = guest.get("booking").get("reference").asString();
        String token = guest.get("manageToken").asString();
        mvc.perform(post("/v1/bookings/" + reference + "/claim").header("X-Booking-Token", token).with(user(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedToAccount").value(true));
        mvc.perform(post("/v1/bookings/" + reference + "/claim").header("X-Booking-Token", token)
                        .with(user(UUID.randomUUID())))
                .andExpect(status().isConflict());

        mvc.perform(get("/v1/bookings/mine").with(user(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/v1/bookings/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void paymentConfirmsOnceAndOnlyForTheRightAmount() throws Exception {
        JsonNode created = read(create(body(CITY_QUOTE, "[]", null)).andExpect(status().isCreated()));
        String reference = created.get("booking").get("reference").asString();
        UUID id = repository.findByReference(reference).orElseThrow().id();

        bookings.paymentSucceeded(id, "pay_wrong", 100, "GBP");
        assertThat(repository.findByReference(reference).orElseThrow().status()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        bookings.paymentSucceeded(id, "pay_1", 6_500, "GBP");
        bookings.paymentSucceeded(id, "pay_1", 6_500, "GBP");
        assertThat(repository.findByReference(reference).orElseThrow().status()).isEqualTo(BookingStatus.CONFIRMED);
        Integer confirmations = jdbc.sql("select count(*) from booking_status_change where booking_id = :id and to_status = 'CONFIRMED'")
                .param("id", id).query(Integer.class).single();
        assertThat(confirmations).isEqualTo(1);
    }

    @Test
    void unpaidBookingsExpireAfterThePaymentWindow() throws Exception {
        JsonNode created = read(create(body(CITY_QUOTE, "[]", null)).andExpect(status().isCreated()));
        String reference = created.get("booking").get("reference").asString();
        jdbc.sql("update booking set created_at = now() - interval '2 hours' where reference = :r").param("r", reference).update();

        bookings.expireUnpaid();

        assertThat(repository.findByReference(reference).orElseThrow().status()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void staffListBookingsCustomersCannot() throws Exception {
        create(body(CITY_QUOTE, "[]", null)).andExpect(status().isCreated());
        mvc.perform(get("/v1/admin/bookings?status=PENDING_PAYMENT").with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING_PAYMENT"));
        mvc.perform(get("/v1/admin/bookings").with(user(UUID.randomUUID()))).andExpect(status().isForbidden());
    }
}
