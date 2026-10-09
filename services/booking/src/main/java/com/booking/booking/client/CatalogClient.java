package com.booking.booking.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.booking.platform.client.ServiceClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Extra limits (how many child seats fit, ...) from catalog-service, cached for five minutes. */
@Component
public class CatalogClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Extra(String code, String name, int maxQuantity) {
    }

    private static final Duration TTL = Duration.ofMinutes(5);

    private final RestClient http;
    private final Clock clock;
    private volatile Map<String, Extra> extras = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public CatalogClient(@Value("${booking.booking.catalog-uri}") String baseUrl, Clock clock) {
        this.http = ServiceClients.create(baseUrl);
        this.clock = clock;
    }

    public Map<String, Extra> extras() {
        if (loadedAt.plus(TTL).isBefore(clock.instant())) {
            List<Extra> list = http.get().uri("/v1/catalog/extras").retrieve()
                    .body(new ParameterizedTypeReference<List<Extra>>() {
                    });
            extras = list == null ? Map.of() : list.stream().collect(Collectors.toMap(Extra::code, e -> e));
            loadedAt = clock.instant();
        }
        return extras;
    }
}
