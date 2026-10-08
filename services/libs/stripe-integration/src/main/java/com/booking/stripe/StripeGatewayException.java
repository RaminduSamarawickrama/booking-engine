package com.booking.stripe;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;

/** A Stripe call failed. {@link #retryable()} tells callers whether a retry with the same idempotency key is safe. */
public final class StripeGatewayException extends RuntimeException {

  private final String stripeCode;
  private final String requestId;
  private final boolean retryable;

  public StripeGatewayException(String operation, StripeException cause) {
    super(operation + " failed: " + cause.getMessage(), cause);
    this.stripeCode = cause.getCode();
    this.requestId = cause.getRequestId();
    this.retryable =
        cause instanceof RateLimitException
            || cause instanceof ApiConnectionException
            || (cause instanceof ApiException && cause.getStatusCode() != null && cause.getStatusCode() >= 500);
  }

  public String stripeCode() {
    return stripeCode;
  }

  /** Stripe's request ID, for support and log correlation. */
  public String requestId() {
    return requestId;
  }

  public boolean retryable() {
    return retryable;
  }
}
