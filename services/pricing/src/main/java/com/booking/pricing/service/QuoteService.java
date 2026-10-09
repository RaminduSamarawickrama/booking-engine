package com.booking.pricing.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.booking.platform.error.ApiException;
import com.booking.pricing.client.MapsProvider;
import com.booking.pricing.config.PricingSettings;
import com.booking.pricing.domain.Place;
import com.booking.pricing.domain.PricingRules;
import com.booking.pricing.domain.Quote;
import com.booking.pricing.domain.QuoteCalculator;
import com.booking.pricing.domain.Route;
import com.booking.pricing.repository.PricingRulesRepository;
import com.booking.pricing.repository.QuoteRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Prices a journey for every vehicle that fits and holds the price for a while. */
@Service
public class QuoteService {

    private final QuoteRepository quotes;
    private final PricingRulesRepository rules;
    private final MapsProvider maps;
    private final PricingSettings settings;
    private final Clock clock;

    public QuoteService(QuoteRepository quotes, PricingRulesRepository rules, MapsProvider maps, PricingSettings settings,
            Clock clock) {
        this.quotes = quotes;
        this.rules = rules;
        this.maps = maps;
        this.settings = settings;
        this.clock = clock;
    }

    public record QuoteRequest(String pickupPlaceId, String dropoffPlaceId, OffsetDateTime pickupAt, int passengers,
            int luggage) {
    }

    @Transactional
    public Quote create(QuoteRequest request) {
        Place pickup = place(request.pickupPlaceId(), "pickup");
        Place dropoff = place(request.dropoffPlaceId(), "dropoff");
        if (pickup.id().equals(dropoff.id())) {
            throw ApiException.unprocessable("same_pickup_and_dropoff", "Pickup and drop-off must be different places.");
        }
        Instant now = clock.instant();
        Instant pickupAt = request.pickupAt().toInstant();
        if (pickupAt.isBefore(now.plus(settings.minimumLeadTime()))) {
            throw ApiException.unprocessable("pickup_too_soon",
                    "Book at least " + settings.minimumLeadTime().toHours() + " hours before pickup.");
        }
        if (pickupAt.isAfter(now.plus(settings.maximumAdvance()))) {
            throw ApiException.unprocessable("pickup_too_far", "Rides can be booked up to a year ahead.");
        }

        Route route = maps.route(pickup, dropoff);
        PricingRules pricing = rules.load();
        List<QuoteCalculator.Option> options = QuoteCalculator.price(pickup, dropoff, route,
                pickupAt.atZone(settings.zone()), request.passengers(), request.luggage(), pricing);
        if (options.isEmpty()) {
            throw ApiException.unprocessable("no_vehicle_fits",
                    "No single vehicle takes this many passengers and bags. Split the group or contact us.");
        }
        List<Quote.ExtraPrice> extras = pricing.extraPrices().entrySet().stream()
                .map(e -> new Quote.ExtraPrice(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Quote.ExtraPrice::code))
                .toList();
        Instant created = now.truncatedTo(ChronoUnit.SECONDS);
        Quote quote = new Quote(UUID.randomUUID(), created, created.plus(settings.quoteValidity()), settings.currency(),
                request.pickupAt(), pickup, dropoff, request.passengers(), request.luggage(),
                route.distanceMeters(), route.durationSeconds(), options, extras);
        quotes.insert(quote);
        return quote;
    }

    /** Returns the quote even after expiry; callers decide whether an expired price is acceptable. */
    @Transactional(readOnly = true)
    public Optional<Quote> find(UUID id) {
        return quotes.findById(id);
    }

    @Scheduled(cron = "0 23 * * * *")
    @Transactional
    public void purgeOld() {
        quotes.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(30)));
    }

    private Place place(String id, String role) {
        return maps.find(id).orElseThrow(() -> ApiException.unprocessable("unknown_place",
                "We couldn't find that " + role + " location. Pick it from the suggestions."));
    }
}
