package com.booking.booking.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** IATA flight numbers such as "BA 117" or "u2 8921", normalised to "BA117" / "U28921". */
public final class FlightNumbers {

    private static final Pattern FLIGHT = Pattern.compile("^([A-Z0-9]{2}|[A-Z]{3})[0-9]{1,4}[A-Z]?$");

    private FlightNumbers() {
    }

    public static Optional<String> normalise(String typed) {
        if (typed == null || typed.isBlank()) {
            return Optional.empty();
        }
        String compact = typed.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        return FLIGHT.matcher(compact).matches() ? Optional.of(compact) : Optional.empty();
    }
}
