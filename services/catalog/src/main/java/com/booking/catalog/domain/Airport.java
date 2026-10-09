package com.booking.catalog.domain;

import java.util.List;

public record Airport(String iata, String name, String city, double latitude, double longitude,
        List<Terminal> terminals) {

    public Airport {
        terminals = List.copyOf(terminals);
    }

    public record Terminal(String code, String name, double latitude, double longitude) {
    }
}
