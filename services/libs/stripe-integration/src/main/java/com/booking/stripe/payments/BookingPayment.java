package com.booking.stripe.payments;

import java.util.List;
import java.util.Objects;

/**
 * What the customer pays for one booking: one line per journey leg (outbound, return),
 * already priced by pricing-service. Amounts are in minor units (pence).
 *
 * @param bookingId internal booking ID (stable; used for idempotency)
 * @param bookingReference customer-facing reference; also the Stripe transfer group
 * @param attempt payment attempt number, so a retry after failure gets a fresh idempotency key
 */
public record BookingPayment(
    String bookingId,
    String bookingReference,
    int attempt,
    String currency,
    String customerEmail,
    List<Leg> legs) {

  public BookingPayment {
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(bookingReference, "bookingReference");
    Objects.requireNonNull(currency, "currency");
    if (attempt < 1) throw new IllegalArgumentException("attempt starts at 1");
    if (!currency.matches("^[a-z]{3}$")) throw new IllegalArgumentException("currency must be lowercase ISO 4217");
    if (legs == null || legs.isEmpty() || legs.size() > 2) {
      throw new IllegalArgumentException("a booking has one or two legs");
    }
    legs = List.copyOf(legs);
  }

  public long totalMinor() {
    return legs.stream().mapToLong(Leg::amountMinor).sum();
  }

  /** Groups the customer charge with the later driver transfers in the Stripe Dashboard and reports. */
  public String transferGroup() {
    return "booking_" + bookingReference;
  }

  /**
   * One journey leg as the customer sees it on the payment page.
   *
   * @param legId internal leg (ride) ID
   * @param description e.g. "Heathrow T5 to SW1A 1AA, 14 Oct 09:30"
   */
  public record Leg(String legId, String description, long amountMinor) {
    public Leg {
      Objects.requireNonNull(legId, "legId");
      Objects.requireNonNull(description, "description");
      if (amountMinor <= 0) throw new IllegalArgumentException("leg amount must be positive");
    }
  }
}
