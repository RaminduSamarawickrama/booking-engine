package com.booking.stripe;

import com.stripe.StripeClient;
import com.stripe.net.RequestOptions;

/** Creates per-service StripeClient instances; never uses the deprecated global API key. */
public final class StripeClients {

  private StripeClients() {}

  public static StripeClient create(StripeSettings settings) {
    return StripeClient.builder()
        .setApiKey(settings.apiKey())
        // Stripe retries are safe because every write carries an idempotency key.
        .setMaxNetworkRetries(2)
        .build();
  }

  /** Every write goes through here so it always carries an idempotency key. */
  public static RequestOptions idempotentKey(String key) {
    if (key == null || key.isBlank() || key.length() > 255) {
      throw new IllegalArgumentException("Idempotency key must be 1-255 characters");
    }
    return RequestOptions.builder().setIdempotencyKey(key).build();
  }
}
