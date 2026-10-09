package com.booking.payment.client;

import java.util.Optional;
import java.util.UUID;

import com.booking.platform.client.ServiceClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads a booking from booking-service with the customer's own credentials (their manage
 * token or access token), so only someone allowed to see a booking can pay for it.
 */
@Component
public class BookingClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingSummary(UUID bookingId, String reference, String status, int totalMinor, String currency,
            String customerEmail) {
    }

    private final RestClient http;

    public BookingClient(@Value("${booking.payments.booking-uri}") String baseUrl) {
        this.http = ServiceClients.create(baseUrl);
    }

    public Optional<BookingSummary> find(String reference, String manageToken, String authorization) {
        return Optional.ofNullable(http.get().uri("/v1/bookings/{reference}", reference)
                .headers(h -> {
                    if (manageToken != null) {
                        h.set("X-Booking-Token", manageToken);
                    }
                    if (authorization != null) {
                        h.set("Authorization", authorization);
                    }
                })
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404 || status == 401 || status == 403) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("booking-service answered " + status);
                    }
                    return response.bodyTo(BookingSummary.class);
                }));
    }
}
