package com.booking.stripe.invoicing;

import static com.booking.stripe.StripeClients.idempotentKey;

import com.booking.stripe.StripeGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.InvoiceCreateParams;
import com.stripe.param.InvoiceItemCreateParams;
import java.util.List;
import java.util.Objects;

/**
 * Monthly invoices for corporate customers who book on account and pay on terms.
 *
 * <p>Invoices live on the platform account (the platform is merchant of record) with no
 * transfer_data. Drivers are paid per completed ride through
 * {@link com.booking.stripe.connect.DriverTransferGateway}, independently of invoice payment.
 */
public final class CorporateInvoiceGateway {

  private final StripeClient stripe;

  public CorporateInvoiceGateway(StripeClient stripe) {
    this.stripe = Objects.requireNonNull(stripe);
  }

  /** Creates the Stripe customer for a corporate account. Idempotent per account. */
  public String createCorporateCustomer(String corporateAccountId, String companyName, String billingEmail) {
    CustomerCreateParams params =
        CustomerCreateParams.builder()
            .setName(companyName)
            .setEmail(billingEmail)
            .putMetadata("corporate_account_id", corporateAccountId)
            .build();
    try {
      Customer customer =
          stripe.v1().customers().create(params, idempotentKey("corporate-customer:" + corporateAccountId));
      return customer.getId();
    } catch (StripeException e) {
      throw new StripeGatewayException("Create customer for corporate account " + corporateAccountId, e);
    }
  }

  /**
   * Builds, finalizes and emails one invoice for a billing period. Each step has its own
   * idempotency key, so a retry after a partial failure resumes instead of duplicating lines.
   *
   * @param periodKey e.g. "2026-10"; one invoice per customer per period
   */
  public IssuedInvoice issueMonthlyInvoice(
      String stripeCustomerId, String periodKey, String currency, long daysUntilDue, List<InvoiceLine> lines) {
    if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("an invoice needs at least one line");
    if (daysUntilDue < 1 || daysUntilDue > 90) throw new IllegalArgumentException("daysUntilDue must be 1-90");
    String base = "invoice:" + stripeCustomerId + ":" + periodKey;
    try {
      Invoice draft =
          stripe.v1().invoices().create(
              InvoiceCreateParams.builder()
                  .setCustomer(stripeCustomerId)
                  .setCollectionMethod(InvoiceCreateParams.CollectionMethod.SEND_INVOICE)
                  .setDaysUntilDue(daysUntilDue)
                  .setAutoAdvance(false)
                  .setPendingInvoiceItemsBehavior(InvoiceCreateParams.PendingInvoiceItemsBehavior.EXCLUDE)
                  .setCurrency(currency)
                  .putMetadata("billing_period", periodKey)
                  .build(),
              idempotentKey(base + ":create"));
      for (InvoiceLine line : lines) {
        stripe.v1().invoiceItems().create(
            InvoiceItemCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setInvoice(draft.getId())
                .setAmount(line.amountMinor())
                .setCurrency(currency)
                .setDescription(line.description())
                .putMetadata("ride_id", line.rideId())
                .putMetadata("booking_reference", line.bookingReference())
                .build(),
            idempotentKey(base + ":line:" + line.rideId()));
      }
      Invoice finalized = stripe.v1().invoices().finalizeInvoice(draft.getId(), idempotentKey(base + ":finalize"));
      Invoice sent = stripe.v1().invoices().sendInvoice(finalized.getId(), idempotentKey(base + ":send"));
      return new IssuedInvoice(sent.getId(), sent.getNumber(), sent.getHostedInvoiceUrl(), sent.getAmountDue());
    } catch (StripeException e) {
      throw new StripeGatewayException("Issue invoice " + periodKey + " for " + stripeCustomerId, e);
    }
  }

  /** One completed ride leg billed to a corporate account. */
  public record InvoiceLine(String rideId, String bookingReference, String description, long amountMinor) {
    public InvoiceLine {
      Objects.requireNonNull(rideId, "rideId");
      if (amountMinor <= 0) throw new IllegalArgumentException("line amount must be positive");
    }
  }

  public record IssuedInvoice(String invoiceId, String number, String hostedInvoiceUrl, Long amountDueMinor) {}
}
