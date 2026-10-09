package com.booking.pricing.quotes;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.booking.platform.web.ApiException;
import com.booking.pricing.maps.MapsProvider;
import com.booking.pricing.maps.Place;
import com.booking.pricing.maps.Route;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

@Service
public class Quotes {

    private final JdbcClient jdbc;
    private final MapsProvider maps;
    private final PricingSettings settings;
    private final JsonMapper json;
    private final Clock clock;

    public Quotes(JdbcClient jdbc, MapsProvider maps, PricingSettings settings, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.maps = maps;
        this.settings = settings;
        this.json = json;
        this.clock = clock;
    }

    public record Request(String pickupPlaceId, String dropoffPlaceId, OffsetDateTime pickupAt, int passengers,
            int luggage) {
    }

    public Quote create(Request request) {
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
        PricingRules rules = rules();
        List<QuoteCalculator.Option> options = QuoteCalculator.price(pickup, dropoff, route,
                pickupAt.atZone(settings.zone()), request.passengers(), request.luggage(), rules);
        if (options.isEmpty()) {
            throw ApiException.unprocessable("no_vehicle_fits",
                    "No single vehicle takes this many passengers and bags. Split the group or contact us.");
        }
        List<Quote.ExtraPrice> extras = rules.extraPrices().entrySet().stream()
                .map(e -> new Quote.ExtraPrice(e.getKey(), e.getValue())).sorted(java.util.Comparator.comparing(Quote.ExtraPrice::code)).toList();
        Instant created = now.truncatedTo(ChronoUnit.SECONDS);
        Quote quote = new Quote(UUID.randomUUID(), created, created.plus(settings.quoteValidity()), settings.currency(),
                request.pickupAt(), pickup, dropoff, request.passengers(), request.luggage(),
                route.distanceMeters(), route.durationSeconds(), options, extras);
        jdbc.sql("insert into quote (id, created_at, expires_at, body) values (:id, :created, :expires, cast(:body as jsonb))")
                .param("id", quote.id())
                .param("created", Timestamp.from(quote.createdAt()))
                .param("expires", Timestamp.from(quote.expiresAt()))
                .param("body", json.writeValueAsString(quote))
                .update();
        return quote;
    }

    /** Returns the quote even after expiry; callers decide whether an expired price is acceptable. */
    public Optional<Quote> find(UUID id) {
        return jdbc.sql("select body::text from quote where id = :id").param("id", id)
                .query(String.class).optional().map(body -> json.readValue(body, Quote.class));
    }

    @Scheduled(cron = "0 23 * * * *")
    public void purgeOld() {
        jdbc.sql("delete from quote where expires_at < :cutoff")
                .param("cutoff", Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(30)))).update();
    }

    private Place place(String id, String role) {
        return maps.find(id).orElseThrow(() -> ApiException.unprocessable("unknown_place",
                "We couldn't find that " + role + " location. Pick it from the suggestions."));
    }

    private PricingRules rules() {
        List<PricingRules.RateCard> cards = jdbc.sql("""
                select category_code, base_fare_minor, per_km_minor, per_minute_minor, minimum_fare_minor,
                       max_passengers, max_luggage
                from rate_card order by base_fare_minor, category_code
                """).query(PricingRules.RateCard.class).list();
        Map<String, PricingRules.AirportCharge> airports = jdbc.sql(
                "select iata, pickup_fee_minor, dropoff_fee_minor from airport_charge")
                .query(PricingRules.AirportCharge.class).list().stream()
                .collect(Collectors.toMap(PricingRules.AirportCharge::iata, a -> a));
        Map<String, Integer> extras = jdbc.sql("select code, price_minor from extra_price")
                .query((rs, row) -> Map.entry(rs.getString(1), rs.getInt(2))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<PricingRules.Surcharge> surcharges = jdbc.sql(
                "select id, percent_bps, from_hour, to_hour from surcharge_rule")
                .query(PricingRules.Surcharge.class).list();
        return new PricingRules(cards, airports, extras, surcharges);
    }
}
