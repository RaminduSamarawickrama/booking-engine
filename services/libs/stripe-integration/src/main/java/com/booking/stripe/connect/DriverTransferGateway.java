package com.booking.stripe.connect;

import static com.booking.stripe.StripeClients.idempotentKey;

import com.booking.stripe.StripeGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversal;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferReversalCreateParams;
import java.util.Objects;

/**
 * Pays drivers after each completed ride leg and recovers money on refunds and disputes.
 *
 * <p>Transfers name the booking's charge as {@code source_transaction}, so a transfer waits
 * for that charge's funds to become available instead of drawing on the platform balance.
 * Stripe never reverses these transfers automatically: refunds after a ride and every dispute
 * need an explicit {@link #reverse} call.
 */
public final class DriverTransferGateway {

  private final StripeClient stripe;
  private final CommissionPolicy commission;

  public DriverTransferGateway(StripeClient stripe, CommissionPolicy commission) {
    this.stripe = Objects.requireNonNull(stripe);
    this.commission = Objects.requireNonNull(commission);
  }

  /**
   * Transfers the driver's share for one completed leg. Idempotent per ride: calling it again
   * for the same ride returns the original transfer instead of paying twice.
   *
   * <p>Callers must check {@link DriverAccountGateway.PayoutReadiness#canReceiveTransfers()}
   * first and queue the payout if the driver is not ready yet.
   */
  public DriverTransferResult payDriverForRide(RidePayout payout) {
    long amount = commission.driverShareMinor(payout.commissionableMinor());
    if (amount <= 0) {
      throw new IllegalArgumentException("driver share is zero for ride " + payout.rideId());
    }
    TransferCreateParams params =
        TransferCreateParams.builder()
            .setAmount(amount)
            .setCurrency(payout.currency())
            .setDestination(payout.driverAccountId())
            .setSourceTransaction(payout.sourceChargeId())
            .setTransferGroup("booking_" + payout.bookingReference())
            .setDescription("Ride " + payout.rideId() + " for booking " + payout.bookingReference())
            .putMetadata("ride_id", payout.rideId())
            .putMetadata("booking_reference", payout.bookingReference())
            .putMetadata("commission_bps", Integer.toString(commission.commissionBasisPoints()))
            .putMetadata("commissionable_minor", Long.toString(payout.commissionableMinor()))
            .build();
    try {
      Transfer transfer = stripe.v1().transfers().create(params, idempotentKey("transfer:ride:" + payout.rideId()));
      return new DriverTransferResult(transfer.getId(), amount, payout.commissionableMinor() - amount);
    } catch (StripeException e) {
      throw new StripeGatewayException("Transfer for ride " + payout.rideId(), e);
    }
  }

  /**
   * Pulls money back from a driver after a refund or dispute. If the driver's balance is too
   * low the reversal can take it negative; that recovery relies on the platform owning
   * negative balance liability (losses_collector: application).
   *
   * @param amountMinor null reverses the whole transfer
   * @param reasonKey stable ID of what caused this (refund or dispute ID), for idempotency
   */
  public String reverse(String transferId, Long amountMinor, String reasonKey) {
    TransferReversalCreateParams.Builder params =
        TransferReversalCreateParams.builder().putMetadata("reason_key", reasonKey);
    if (amountMinor != null) {
      if (amountMinor <= 0) throw new IllegalArgumentException("reversal amount must be positive");
      params.setAmount(amountMinor);
    }
    try {
      TransferReversal reversal =
          stripe.v1().transfers().reversals().create(
              transferId, params.build(), idempotentKey("reversal:" + transferId + ":" + reasonKey));
      return reversal.getId();
    } catch (StripeException e) {
      throw new StripeGatewayException("Reverse transfer " + transferId, e);
    }
  }

  /**
   * @param sourceChargeId the booking's charge (PaymentIntent.latest_charge), not the PaymentIntent ID
   * @param commissionableMinor leg amount the commission applies to
   */
  public record RidePayout(
      String rideId,
      String bookingReference,
      String driverAccountId,
      String sourceChargeId,
      long commissionableMinor,
      String currency) {
    public RidePayout {
      Objects.requireNonNull(rideId, "rideId");
      Objects.requireNonNull(bookingReference, "bookingReference");
      if (driverAccountId == null || !driverAccountId.startsWith("acct_")) {
        throw new IllegalArgumentException("driverAccountId must be a connected account ID");
      }
      if (sourceChargeId == null || !(sourceChargeId.startsWith("ch_") || sourceChargeId.startsWith("py_"))) {
        throw new IllegalArgumentException("sourceChargeId must be a charge ID (ch_ or py_)");
      }
      if (currency == null || !currency.matches("^[a-z]{3}$")) throw new IllegalArgumentException("currency");
    }
  }

  public record DriverTransferResult(String transferId, long driverAmountMinor, long platformAmountMinor) {}
}
