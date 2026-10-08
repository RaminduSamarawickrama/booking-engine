package com.booking.stripe.connect;

/**
 * Transfer math for separate charges and transfers: the platform keeps its commission by
 * transferring less than the customer paid for the leg. application_fee_amount is not used
 * (it is not compatible with this charge pattern).
 *
 * <p>Stripe's processing fees come out of the platform's share.
 */
public final class CommissionPolicy {

  private static final int BASIS_POINTS = 10_000;

  private final int commissionBasisPoints;

  /** @param commissionBasisPoints platform commission, e.g. 2500 = 25% */
  public CommissionPolicy(int commissionBasisPoints) {
    if (commissionBasisPoints < 0 || commissionBasisPoints >= BASIS_POINTS) {
      throw new IllegalArgumentException("commission must be between 0% and 99.99%");
    }
    this.commissionBasisPoints = commissionBasisPoints;
  }

  public int commissionBasisPoints() {
    return commissionBasisPoints;
  }

  /**
   * Driver's share of a leg, rounded down to the minor unit so rounding never moves money
   * from the platform's balance to a driver. Uses exact integer arithmetic.
   *
   * @param commissionableMinor the leg amount the commission applies to (see open question on
   *     VAT, discounts and paid extras in connect-recommend-plan.md)
   */
  public long driverShareMinor(long commissionableMinor) {
    if (commissionableMinor < 0) throw new IllegalArgumentException("amount must not be negative");
    return Math.multiplyExact(commissionableMinor, (long) (BASIS_POINTS - commissionBasisPoints)) / BASIS_POINTS;
  }

  public long platformShareMinor(long commissionableMinor) {
    return commissionableMinor - driverShareMinor(commissionableMinor);
  }
}
