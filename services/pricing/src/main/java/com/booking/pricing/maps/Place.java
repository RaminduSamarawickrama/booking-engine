package com.booking.pricing.maps;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A pickup or drop-off point. {@code airportIata} and {@code terminal} are set for airport
 * terminals, which carry airport charges and let the driver track the flight.
 */
public record Place(String id, String name, String address, double latitude, double longitude,
        String airportIata, String terminal) {

    @JsonIgnore
    public boolean isAirport() {
        return airportIata != null;
    }
}
