package com.booking.pricing.repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.booking.pricing.domain.PricingRules;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Rate cards, airport charges, extra prices and surcharges. */
@Repository
public class PricingRulesRepository {

    private final JdbcClient jdbc;

    public PricingRulesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PricingRules load() {
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
