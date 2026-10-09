package com.booking.pricing.domain;

public record Route(int distanceMeters, int durationSeconds) {

    public double kilometres() {
        return distanceMeters / 1000.0;
    }

    public double minutes() {
        return durationSeconds / 60.0;
    }
}
