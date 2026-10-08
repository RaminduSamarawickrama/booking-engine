package com.booking.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestIdsTest {

    @Test
    void keepsASafeCallerSuppliedId() {
        assertThat(RequestIds.acceptOrCreate("gw-1234abcd")).isEqualTo("gw-1234abcd");
    }

    @Test
    void replacesMissingUnsafeOrOversizedIds() {
        assertThat(RequestIds.acceptOrCreate(null)).hasSize(36);
        assertThat(RequestIds.acceptOrCreate("abc\nFAKE LOG LINE")).hasSize(36).doesNotContain("FAKE");
        assertThat(RequestIds.acceptOrCreate("x".repeat(65))).hasSize(36);
        assertThat(RequestIds.acceptOrCreate("short")).hasSize(36);
    }
}
