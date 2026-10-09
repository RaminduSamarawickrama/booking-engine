package com.booking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainTest {

    @Test
    void statusesOnlyMoveForward() {
        assertThat(BookingStatus.PENDING_PAYMENT.canMoveTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.PENDING_PAYMENT.canMoveTo(BookingStatus.EXPIRED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canMoveTo(BookingStatus.PENDING_PAYMENT)).isFalse();
        assertThat(BookingStatus.EXPIRED.canMoveTo(BookingStatus.CONFIRMED)).isFalse();
        assertThat(BookingStatus.CANCELLED.isFinal()).isTrue();
    }

    @Test
    void referencesAreEasyToReadAloud() {
        for (int i = 0; i < 500; i++) {
            assertThat(References.next()).matches("TR[23456789A-HJKMNP-Z]{6}").doesNotContain("0", "O", "1", "I", "L");
        }
        assertThat(References.normalise(" tr-7k4 p9q ")).isEqualTo("TR7K4P9Q");
    }

    @Test
    void flightNumbersAreNormalised() {
        assertThat(FlightNumbers.normalise("ba 117")).contains("BA117");
        assertThat(FlightNumbers.normalise("U2 8921")).contains("U28921");
        assertThat(FlightNumbers.normalise("EZY8921")).contains("EZY8921");
        assertThat(FlightNumbers.normalise("hello")).isEmpty();
        assertThat(FlightNumbers.normalise("  ")).isEmpty();
    }
}
