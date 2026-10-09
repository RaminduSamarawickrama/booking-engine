package com.booking.pricing;

import com.booking.architecture.LayeredArchitecture;

import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void followsTheLayeredArchitecture() {
        LayeredArchitecture.check("com.booking.pricing");
    }
}
