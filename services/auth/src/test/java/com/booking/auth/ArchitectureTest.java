package com.booking.auth;

import com.booking.architecture.LayeredArchitecture;

import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void followsTheLayeredArchitecture() {
        LayeredArchitecture.check("com.booking.auth");
    }
}
