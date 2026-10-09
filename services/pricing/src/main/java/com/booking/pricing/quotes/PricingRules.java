package com.booking.pricing.quotes;

import java.util.List;
import java.util.Map;

/** Everything a price depends on besides the journey, loaded from the database per quote. */
public record PricingRules(List<RateCard> rateCards, Map<String, AirportCharge> airportCharges,
        Map<String, Integer> extraPrices, List<Surcharge> surcharges) {

    public record RateCard(String categoryCode, int baseFareMinor, int perKmMinor, int perMinuteMinor,
            int minimumFareMinor, int maxPassengers, int maxLuggage) {
    }

    public record AirportCharge(String iata, int pickupFeeMinor, int dropoffFeeMinor) {
    }

    /** Applies when the local pickup hour is in [fromHour, toHour), wrapping past midnight. */
    public record Surcharge(String id, int percentBps, int fromHour, int toHour) {
        public boolean appliesAt(int hour) {
            return fromHour <= toHour ? hour >= fromHour && hour < toHour : hour >= fromHour || hour < toHour;
        }
    }
}
