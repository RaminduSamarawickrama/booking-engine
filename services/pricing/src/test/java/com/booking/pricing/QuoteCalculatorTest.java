package com.booking.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.booking.pricing.maps.Place;
import com.booking.pricing.maps.Route;
import com.booking.pricing.quotes.PricingRules;
import com.booking.pricing.quotes.QuoteCalculator;

import org.junit.jupiter.api.Test;

class QuoteCalculatorTest {

    static final ZoneId LONDON = ZoneId.of("Europe/London");
    static final Place HEATHROW = new Place("LHR-T5", "Heathrow T5", "", 51.47, -0.48, "LHR", "T5");
    static final Place GATWICK = new Place("LGW-S", "Gatwick South", "", 51.15, -0.16, "LGW", "S");
    static final Place CITY = new Place("kings-cross", "King's Cross", "", 51.53, -0.12, null, null);

    static final PricingRules RULES = new PricingRules(
            List.of(new PricingRules.RateCard("STANDARD", 300, 140, 20, 2500, 3, 3),
                    new PricingRules.RateCard("MPV", 600, 190, 26, 3900, 6, 6)),
            Map.of("LHR", new PricingRules.AirportCharge("LHR", 700, 600),
                    "LGW", new PricingRules.AirportCharge("LGW", 600, 700)),
            Map.of("CHILD_SEAT", 800),
            List.of(new PricingRules.Surcharge("night", 1500, 22, 6)));

    static final Route ROUTE = new Route(30_000, 3_000); // 30 km, 50 min
    static final ZonedDateTime DAYTIME = ZonedDateTime.of(2026, 11, 2, 14, 0, 0, 0, LONDON);

    @Test
    void addsDistanceTimeAndPickupAirportFeeThenRoundsUpToAPound() {
        var standard = QuoteCalculator.price(HEATHROW, CITY, ROUTE, DAYTIME, 2, 2, RULES).get(0);
        // 300 + 140 x 30 + 20 x 50 = 5500; + 700 Heathrow pickup = 6200
        assertThat(standard.breakdown().fareMinor()).isEqualTo(5500);
        assertThat(standard.breakdown().airportFeesMinor()).isEqualTo(700);
        assertThat(standard.priceMinor()).isEqualTo(6200);
    }

    @Test
    void chargesTheDropOffAirportsForecourtFee() {
        var standard = QuoteCalculator.price(CITY, GATWICK, ROUTE, DAYTIME, 1, 1, RULES).get(0);
        assertThat(standard.breakdown().airportFeesMinor()).isEqualTo(700);
    }

    @Test
    void appliesTheMinimumFareToShortTrips() {
        var shortTrip = QuoteCalculator.price(CITY, CITY, new Route(2_000, 600), DAYTIME, 1, 0, RULES).get(0);
        assertThat(shortTrip.breakdown().fareMinor()).isEqualTo(2500);
        assertThat(shortTrip.priceMinor()).isEqualTo(2500);
    }

    @Test
    void addsTheNightSurchargeAcrossMidnightInLocalTime() {
        ZonedDateTime lateEvening = ZonedDateTime.of(2026, 11, 2, 23, 30, 0, 0, LONDON);
        ZonedDateTime earlyMorning = ZonedDateTime.of(2026, 11, 3, 5, 59, 0, 0, LONDON);
        ZonedDateTime sixAm = ZonedDateTime.of(2026, 11, 3, 6, 0, 0, 0, LONDON);
        assertThat(QuoteCalculator.price(CITY, CITY, ROUTE, lateEvening, 1, 0, RULES).get(0).breakdown().surchargeMinor())
                .isEqualTo(825);
        assertThat(QuoteCalculator.price(CITY, CITY, ROUTE, earlyMorning, 1, 0, RULES).get(0).breakdown().surchargeMinor())
                .isEqualTo(825);
        assertThat(QuoteCalculator.price(CITY, CITY, ROUTE, sixAm, 1, 0, RULES).get(0).breakdown().surchargeMinor())
                .isZero();
    }

    @Test
    void offersOnlyVehiclesTheGroupFitsIn() {
        assertThat(QuoteCalculator.price(HEATHROW, CITY, ROUTE, DAYTIME, 3, 3, RULES))
                .extracting(QuoteCalculator.Option::categoryCode).containsExactly("STANDARD", "MPV");
        assertThat(QuoteCalculator.price(HEATHROW, CITY, ROUTE, DAYTIME, 4, 2, RULES))
                .extracting(QuoteCalculator.Option::categoryCode).containsExactly("MPV");
        assertThat(QuoteCalculator.price(HEATHROW, CITY, ROUTE, DAYTIME, 7, 2, RULES)).isEmpty();
    }
}
