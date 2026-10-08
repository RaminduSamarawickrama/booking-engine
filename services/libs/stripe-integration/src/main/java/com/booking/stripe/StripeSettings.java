package com.booking.stripe;

import java.util.Objects;

/**
 * Stripe configuration for one service. Each service gets its own restricted key
 * (rk_...) with only the permissions it needs; secret keys (sk_...) are accepted
 * for local experiments but discouraged.
 *
 * @param apiKey restricted or secret key, read from the environment or a secrets store
 * @param webhookSigningSecret whsec_... for the webhook endpoint this service owns (may be null)
 * @param allowLiveMode false everywhere except production; live keys are refused when false
 */
public record StripeSettings(String apiKey, String webhookSigningSecret, boolean allowLiveMode) {

  public StripeSettings {
    Objects.requireNonNull(apiKey, "Stripe API key is not configured");
    if (!apiKey.matches("^(sk|rk)_(test|live)_[A-Za-z0-9]+$")) {
      throw new IllegalArgumentException("Stripe API key has an unexpected format");
    }
    if (isLive(apiKey) && !allowLiveMode) {
      throw new IllegalArgumentException(
          "A live-mode Stripe key was supplied but live mode is not allowed in this environment");
    }
    if (webhookSigningSecret != null
        && !webhookSigningSecret.isBlank()
        && !webhookSigningSecret.startsWith("whsec_")) {
      throw new IllegalArgumentException("Stripe webhook signing secret must start with whsec_");
    }
  }

  public boolean liveMode() {
    return isLive(apiKey);
  }

  /** True when the key is a full-access secret key; callers should log a warning to move to a restricted key. */
  public boolean usesSecretKey() {
    return apiKey.startsWith("sk_");
  }

  private static boolean isLive(String key) {
    return key.contains("_live_");
  }

  /** Never print the key itself. */
  @Override
  public String toString() {
    return "StripeSettings[mode=" + (liveMode() ? "live" : "test")
        + ", keyType=" + (usesSecretKey() ? "secret" : "restricted")
        + ", webhookSecret=" + (webhookSigningSecret == null || webhookSigningSecret.isBlank() ? "unset" : "set")
        + "]";
  }
}
