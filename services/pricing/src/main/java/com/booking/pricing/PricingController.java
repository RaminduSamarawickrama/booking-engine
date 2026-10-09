package com.booking.pricing;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.booking.platform.web.ApiException;
import com.booking.pricing.maps.MapsProvider;
import com.booking.pricing.maps.Place;
import com.booking.pricing.quotes.Quote;
import com.booking.pricing.quotes.Quotes;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class PricingController {

    private final MapsProvider maps;
    private final Quotes quotes;

    public PricingController(MapsProvider maps, Quotes quotes) {
        this.maps = maps;
        this.quotes = quotes;
    }

    /** Address suggestions as the customer types. */
    @GetMapping("/v1/places")
    public List<Place> places(@RequestParam @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return maps.search(q, limit);
    }

    public record QuoteRequest(
            @NotBlank @Size(max = 100) String pickupPlaceId,
            @NotBlank @Size(max = 100) String dropoffPlaceId,
            @NotNull OffsetDateTime pickupAt,
            @Min(1) @Max(16) int passengers,
            @Min(0) @Max(16) int luggage) {
    }

    @PostMapping("/v1/quotes")
    public ResponseEntity<Quote> create(@Valid @RequestBody QuoteRequest request) {
        Quote quote = quotes.create(new Quotes.Request(request.pickupPlaceId(), request.dropoffPlaceId(),
                request.pickupAt(), request.passengers(), request.luggage()));
        return ResponseEntity.created(URI.create("/v1/quotes/" + quote.id())).body(quote);
    }

    /** Quote ids are random UUIDs, so holding one is what grants access to it. */
    @GetMapping("/v1/quotes/{id}")
    public Quote get(@PathVariable UUID id) {
        return quotes.find(id).orElseThrow(() -> ApiException.notFound("quote_not_found", "That quote doesn't exist."));
    }
}
