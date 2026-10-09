package com.booking.pricing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.booking.platform.test.Infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = { "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "booking.platform.messaging.relay-initial-delay=PT1H", "management.server.port=" })
@AutoConfigureMockMvc
@Import(Infrastructure.class)
class PricingApiTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    String quoteRequest(String pickup, String dropoff, OffsetDateTime at, int passengers, int luggage) {
        return """
                {"pickupPlaceId":"%s","dropoffPlaceId":"%s","pickupAt":"%s","passengers":%d,"luggage":%d}
                """.formatted(pickup, dropoff, at, passengers, luggage);
    }

    @Test
    void suggestsPlacesWithoutSignIn() throws Exception {
        mvc.perform(get("/v1/places").param("q", "gatwick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].airportIata").value("LGW"))
                .andExpect(jsonPath("$[0].airport").doesNotExist());
    }

    @Test
    void quotesEveryVehicleThatFitsAndCanBeReadBack() throws Exception {
        OffsetDateTime tomorrowNoon = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(12);
        String body = mvc.perform(post("/v1/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequest("LHR-T5", "kings-cross", tomorrowNoon, 4, 4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.options[0].categoryCode").value("ESTATE"))
                .andExpect(jsonPath("$.options[?(@.categoryCode == 'STANDARD')]").isEmpty())
                .andExpect(jsonPath("$.extras[?(@.code == 'MEET_GREET')].priceMinor").value(1200))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asString();

        mvc.perform(get("/v1/quotes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickup.terminal").value("T5"))
                .andExpect(jsonPath("$.passengers").value(4));
    }

    @Test
    void rejectsRidesTooSoonAndUnknownPlaces() throws Exception {
        mvc.perform(post("/v1/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequest("LHR-T5", "kings-cross", OffsetDateTime.now().plusMinutes(30), 1, 1)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("pickup_too_soon"));
        mvc.perform(post("/v1/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequest("nowhere", "kings-cross", OffsetDateTime.now().plusDays(2), 1, 1)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("unknown_place"));
        mvc.perform(post("/v1/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequest("LHR-T5", "kings-cross", OffsetDateTime.now().plusDays(2), 12, 1)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("no_vehicle_fits"));
    }
}
