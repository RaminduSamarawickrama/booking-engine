package com.booking.stripe.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CommissionPolicyTest {

  private final CommissionPolicy twentyFivePercent = new CommissionPolicy(2500);

  @Test
  void driverReceives75PercentOfTheLeg() {
    assertEquals(7500, twentyFivePercent.driverShareMinor(10_000)); // £100 leg -> £75
    assertEquals(2500, twentyFivePercent.platformShareMinor(10_000));
  }

  @Test
  void roundingNeverOverpaysTheDriver() {
    // £33.33 leg: 75% is 2499.75p -> driver gets 2499p, platform 834p, nothing lost.
    assertEquals(2499, twentyFivePercent.driverShareMinor(3333));
    assertEquals(834, twentyFivePercent.platformShareMinor(3333));
    assertEquals(3333, twentyFivePercent.driverShareMinor(3333) + twentyFivePercent.platformShareMinor(3333));
  }

  @Test
  void handlesZeroAndLargeAmountsExactly() {
    assertEquals(0, twentyFivePercent.driverShareMinor(0));
    assertEquals(7_500_000_000L, twentyFivePercent.driverShareMinor(10_000_000_000L));
  }

  @Test
  void rejectsInvalidRates() {
    assertThrows(IllegalArgumentException.class, () -> new CommissionPolicy(-1));
    assertThrows(IllegalArgumentException.class, () -> new CommissionPolicy(10_000));
    assertThrows(IllegalArgumentException.class, () -> twentyFivePercent.driverShareMinor(-5));
  }
}
