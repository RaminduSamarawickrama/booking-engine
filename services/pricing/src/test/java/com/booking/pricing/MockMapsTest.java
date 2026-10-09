package com.booking.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.booking.pricing.client.MockMaps;
import com.booking.pricing.domain.Place;

import org.junit.jupiter.api.Test;

class MockMapsTest {

    final MockMaps maps = new MockMaps();

    @Test
    void findsTerminalsByNameOrCode() {
        assertThat(maps.search("heathrow", 10)).extracting(Place::id).contains("LHR-T2", "LHR-T5");
        assertThat(maps.search("heathrow t5", 10)).extracting(Place::id).containsExactly("LHR-T5");
        assertThat(maps.search("LGW", 10)).allMatch(Place::isAirport);
        assertThat(maps.search("x", 10)).isEmpty();
    }

    @Test
    void estimatesHeathrowToCentralLondonRealistically() {
        Place t5 = maps.find("LHR-T5").orElseThrow();
        Place kingsCross = maps.find("kings-cross").orElseThrow();
        var route = maps.route(t5, kingsCross);
        assertThat(route.kilometres()).isBetween(28.0, 38.0);   // ~30 km by road
        assertThat(route.minutes()).isBetween(35.0, 75.0);
    }
}
