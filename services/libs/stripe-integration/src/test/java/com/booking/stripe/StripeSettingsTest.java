package com.booking.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StripeSettingsTest {

  @Test
  void acceptsRestrictedTestKey() {
    StripeSettings settings = new StripeSettings("rk_test_abc123", "whsec_abc", false);
    assertFalse(settings.liveMode());
    assertFalse(settings.usesSecretKey());
  }

  @Test
  void flagsSecretKeysSoServicesCanWarn() {
    assertTrue(new StripeSettings("sk_test_abc123", null, false).usesSecretKey());
  }

  @Test
  void refusesLiveKeysOutsideProduction() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new StripeSettings("rk_live_abc123", null, false));
    assertTrue(e.getMessage().contains("live mode is not allowed"));
  }

  @Test
  void allowsLiveKeysWhenExplicitlyEnabled() {
    assertTrue(new StripeSettings("rk_live_abc123", null, true).liveMode());
  }

  @Test
  void rejectsMalformedKeysAndSecrets() {
    assertThrows(IllegalArgumentException.class, () -> new StripeSettings("pk_test_abc", null, false));
    assertThrows(IllegalArgumentException.class, () -> new StripeSettings("rk_test_abc", "not-a-secret", false));
    assertThrows(NullPointerException.class, () -> new StripeSettings(null, null, false));
  }

  @Test
  void neverPrintsTheKey() {
    String text = new StripeSettings("rk_test_SuperSecretValue", "whsec_AlsoSecret", false).toString();
    assertFalse(text.contains("SuperSecretValue"));
    assertFalse(text.contains("AlsoSecret"));
    assertEquals("StripeSettings[mode=test, keyType=restricted, webhookSecret=set]", text);
  }
}
