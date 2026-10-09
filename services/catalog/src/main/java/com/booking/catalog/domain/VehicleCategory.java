package com.booking.catalog.domain;

/** A class of vehicle customers choose from, e.g. Standard or People carrier. */
public record VehicleCategory(String code, String name, String description, String exampleModels,
        int maxPassengers, int maxLuggage, int sortOrder, boolean active) {

    /** Codes are stable identifiers shared with pricing-service and bookings. */
    public static boolean isValidCode(String code) {
        return code != null && code.matches("^[A-Z][A-Z0-9_]{1,39}$");
    }
}
