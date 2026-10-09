package com.booking.booking.client;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.booking.platform.client.ServiceClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/** Reads quotes from pricing-service, so a booking charges exactly what was quoted. */
@Component
public class PricingClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(UUID id, Instant expiresAt, String currency, OffsetDateTime pickupAt, JsonNode pickup,
            JsonNode dropoff, int passengers, int luggage, int distanceMeters, int durationSeconds,
            List<Option> options, List<ExtraPrice> extras) {

        public boolean pickupIsAirport() {
            JsonNode iata = pickup.get("airportIata");
            return iata != null && !iata.isNull();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String categoryCode, int priceMinor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtraPrice(String code, int priceMinor) {
    }

    private final RestClient http;

    public PricingClient(@Value("${booking.booking.pricing-uri}") String baseUrl) {
        this.http = ServiceClients.create(baseUrl);
    }

    public Optional<Quote> quote(UUID id) {
        return Optional.ofNullable(http.get().uri("/v1/quotes/{id}", id).exchange((request, response) -> {
            if (response.getStatusCode().value() == 404) {
                return null;
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("pricing-service answered " + response.getStatusCode());
            }
            return response.bodyTo(Quote.class);
        }));
    }
}
