package com.booking.pricing.web;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.booking.platform.error.ApiException;
import com.booking.pricing.domain.Quote;
import com.booking.pricing.service.QuoteService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quotes")
public class QuoteController {

    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    public record QuoteRequest(
            @NotBlank @Size(max = 100) String pickupPlaceId,
            @NotBlank @Size(max = 100) String dropoffPlaceId,
            @NotNull OffsetDateTime pickupAt,
            @Min(1) @Max(16) int passengers,
            @Min(0) @Max(16) int luggage) {
    }

    @PostMapping
    public ResponseEntity<Quote> create(@Valid @RequestBody QuoteRequest request) {
        Quote quote = quotes.create(new QuoteService.QuoteRequest(request.pickupPlaceId(), request.dropoffPlaceId(),
                request.pickupAt(), request.passengers(), request.luggage()));
        return ResponseEntity.created(URI.create("/v1/quotes/" + quote.id())).body(quote);
    }

    /** Quote ids are random UUIDs, so holding one is what grants access to it. */
    @GetMapping("/{id}")
    public Quote get(@PathVariable UUID id) {
        return quotes.find(id).orElseThrow(() -> ApiException.notFound("quote_not_found", "That quote doesn't exist."));
    }
}
