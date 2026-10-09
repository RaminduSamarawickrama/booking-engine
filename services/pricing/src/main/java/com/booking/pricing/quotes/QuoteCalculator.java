package com.booking.pricing.quotes;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.booking.pricing.maps.Place;
import com.booking.pricing.maps.Route;

/**
 * Prices a journey for every vehicle category that fits the party. Pure: no I/O, no clock,
 * so every rule is unit tested.
 *
 * <pre>
 * fare     = max(base + perKm x km + perMinute x minutes, minimum fare)
 * + night  = fare x surcharge % (pickup hour, local time)
 * + fees   = pickup airport's parking fee + drop-off airport's forecourt charge
 * total    = rounded up to the next whole pound
 * </pre>
 */
public final class QuoteCalculator {

    private QuoteCalculator() {
    }

    public record Breakdown(int fareMinor, int surchargeMinor, int airportFeesMinor, int roundingMinor) {
        public int totalMinor() {
            return fareMinor + surchargeMinor + airportFeesMinor + roundingMinor;
        }
    }

    public record Option(String categoryCode, int priceMinor, Breakdown breakdown) {
    }

    public static List<Option> price(Place pickup, Place dropoff, Route route, ZonedDateTime localPickupTime,
            int passengers, int luggage, PricingRules rules) {
        int airportFees = 0;
        if (pickup.isAirport() && rules.airportCharges().containsKey(pickup.airportIata())) {
            airportFees += rules.airportCharges().get(pickup.airportIata()).pickupFeeMinor();
        }
        if (dropoff.isAirport() && rules.airportCharges().containsKey(dropoff.airportIata())) {
            airportFees += rules.airportCharges().get(dropoff.airportIata()).dropoffFeeMinor();
        }
        int surchargeBps = rules.surcharges().stream()
                .filter(s -> s.appliesAt(localPickupTime.getHour()))
                .mapToInt(PricingRules.Surcharge::percentBps)
                .sum();

        List<Option> options = new ArrayList<>();
        for (PricingRules.RateCard card : rules.rateCards()) {
            if (passengers > card.maxPassengers() || luggage > card.maxLuggage()) {
                continue;
            }
            long raw = card.baseFareMinor()
                    + Math.round(card.perKmMinor() * route.kilometres())
                    + Math.round(card.perMinuteMinor() * route.minutes());
            int fare = (int) Math.max(raw, card.minimumFareMinor());
            int surcharge = (int) Math.round(fare * surchargeBps / 10_000.0);
            int subtotal = fare + surcharge + airportFees;
            int rounding = (100 - subtotal % 100) % 100;
            options.add(new Option(card.categoryCode(), subtotal + rounding,
                    new Breakdown(fare, surcharge, airportFees, rounding)));
        }
        return options;
    }
}
