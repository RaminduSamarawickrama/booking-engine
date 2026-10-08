package com.booking.stripe.connect;

import static com.booking.stripe.StripeClients.idempotentKey;

import com.booking.stripe.StripeGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.LoginLink;
import com.stripe.model.v2.core.Account;
import com.stripe.model.v2.core.AccountLink;
import com.stripe.param.v2.core.AccountCreateParams;
import com.stripe.param.v2.core.AccountLinkCreateParams;
import com.stripe.param.v2.core.AccountRetrieveParams;
import java.util.Objects;
import java.util.Optional;

/**
 * Driver connected accounts on Accounts v2.
 *
 * <p>Configuration (see connect-recommend-plan.md): Express dashboard, platform-owned pricing
 * and negative balance liability, recipient configuration with stripe_transfers. Drivers never
 * take payments directly, so no merchant configuration or card_payments is requested.
 */
public final class DriverAccountGateway {

  private final StripeClient stripe;

  public DriverAccountGateway(StripeClient stripe) {
    this.stripe = Objects.requireNonNull(stripe);
  }

  /** Creates the driver's connected account. Idempotent per driver. */
  public String createDriverAccount(DriverAccountRequest request) {
    AccountCreateParams params =
        AccountCreateParams.builder()
            .setDisplayName(request.displayName())
            .setContactEmail(request.email())
            .setDashboard(AccountCreateParams.Dashboard.EXPRESS)
            .setIdentity(
                AccountCreateParams.Identity.builder()
                    .setCountry(request.country())
                    .setEntityType(AccountCreateParams.Identity.EntityType.INDIVIDUAL)
                    .build())
            .setDefaults(
                AccountCreateParams.Defaults.builder()
                    .setCurrency(request.currency())
                    .setResponsibilities(
                        AccountCreateParams.Defaults.Responsibilities.builder()
                            .setFeesCollector(AccountCreateParams.Defaults.Responsibilities.FeesCollector.APPLICATION)
                            .setLossesCollector(AccountCreateParams.Defaults.Responsibilities.LossesCollector.APPLICATION)
                            .build())
                    .build())
            .setConfiguration(
                AccountCreateParams.Configuration.builder()
                    .setRecipient(
                        AccountCreateParams.Configuration.Recipient.builder()
                            .setCapabilities(
                                AccountCreateParams.Configuration.Recipient.Capabilities.builder()
                                    .setStripeBalance(
                                        AccountCreateParams.Configuration.Recipient.Capabilities.StripeBalance.builder()
                                            .setStripeTransfers(
                                                AccountCreateParams.Configuration.Recipient.Capabilities.StripeBalance
                                                    .StripeTransfers.builder()
                                                    .setRequested(true)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .putMetadata("driver_id", request.driverId())
            .build();
    try {
      Account account =
          stripe.v2().core().accounts().create(params, idempotentKey("driver-account:" + request.driverId()));
      return account.getId();
    } catch (StripeException e) {
      throw new StripeGatewayException("Create connected account for driver " + request.driverId(), e);
    }
  }

  /**
   * Stripe-hosted onboarding link, opened from the driver app. Links expire quickly and are
   * single-use, so create one each time the driver taps "Set up payouts".
   *
   * @param refreshUrl where Stripe sends the driver if the link expired (create a new link there)
   * @param returnUrl where Stripe sends the driver when they finish (then re-check readiness)
   */
  public String createOnboardingLink(String accountId, String refreshUrl, String returnUrl) {
    AccountLinkCreateParams params =
        AccountLinkCreateParams.builder()
            .setAccount(accountId)
            .setUseCase(
                AccountLinkCreateParams.UseCase.builder()
                    .setType(AccountLinkCreateParams.UseCase.Type.ACCOUNT_ONBOARDING)
                    .setAccountOnboarding(
                        AccountLinkCreateParams.UseCase.AccountOnboarding.builder()
                            .addConfiguration(AccountLinkCreateParams.UseCase.AccountOnboarding.Configuration.RECIPIENT)
                            .setRefreshUrl(refreshUrl)
                            .setReturnUrl(returnUrl)
                            .build())
                    .build())
            .build();
    try {
      AccountLink link = stripe.v2().core().accountLinks().create(params);
      return link.getUrl();
    } catch (StripeException e) {
      throw new StripeGatewayException("Create onboarding link for " + accountId, e);
    }
  }

  /** One-time link to the driver's Express dashboard (earnings and payouts). */
  public String createDashboardLoginLink(String accountId) {
    try {
      LoginLink link = stripe.v1().accounts().loginLinks().create(accountId);
      return link.getUrl();
    } catch (StripeException e) {
      throw new StripeGatewayException("Create Express login link for " + accountId, e);
    }
  }

  /**
   * Reads the v2 capability statuses. Transfers to a driver are only allowed when
   * {@link PayoutReadiness#canReceiveTransfers()} is true. Do not use v1
   * charges_enabled / payouts_enabled for this.
   */
  public PayoutReadiness readiness(String accountId) {
    AccountRetrieveParams params =
        AccountRetrieveParams.builder()
            .addInclude(AccountRetrieveParams.Include.CONFIGURATION__RECIPIENT)
            .addInclude(AccountRetrieveParams.Include.REQUIREMENTS)
            .build();
    try {
      return PayoutReadiness.from(stripe.v2().core().accounts().retrieve(accountId, params));
    } catch (StripeException e) {
      throw new StripeGatewayException("Retrieve connected account " + accountId, e);
    }
  }

  /**
   * @param country ISO 3166-1 alpha-2, lowercase (e.g. "gb")
   * @param currency default currency, lowercase ISO 4217 (e.g. "gbp")
   */
  public record DriverAccountRequest(String driverId, String displayName, String email, String country, String currency) {
    public DriverAccountRequest {
      Objects.requireNonNull(driverId, "driverId");
      Objects.requireNonNull(email, "email");
      if (country == null || !country.matches("^[a-z]{2}$")) throw new IllegalArgumentException("country must be lowercase alpha-2");
      if (currency == null || !currency.matches("^[a-z]{3}$")) throw new IllegalArgumentException("currency must be lowercase ISO 4217");
    }
  }

  /** Capability statuses from configuration.recipient on the v2 Account. */
  public record PayoutReadiness(String accountId, String transfersStatus, String payoutsStatus, boolean requirementsOutstanding) {

    public boolean canReceiveTransfers() {
      return "active".equals(transfersStatus);
    }

    static PayoutReadiness from(Account account) {
      var balance =
          Optional.ofNullable(account.getConfiguration())
              .map(Account.Configuration::getRecipient)
              .map(Account.Configuration.Recipient::getCapabilities)
              .map(Account.Configuration.Recipient.Capabilities::getStripeBalance);
      String transfers =
          balance.map(Account.Configuration.Recipient.Capabilities.StripeBalance::getStripeTransfers)
              .map(Account.Configuration.Recipient.Capabilities.StripeBalance.StripeTransfers::getStatus)
              .orElse("unrequested");
      String payouts =
          balance.map(Account.Configuration.Recipient.Capabilities.StripeBalance::getPayouts)
              .map(Account.Configuration.Recipient.Capabilities.StripeBalance.Payouts::getStatus)
              .orElse("unrequested");
      boolean outstanding =
          Optional.ofNullable(account.getRequirements())
              .map(Account.Requirements::getEntries)
              .map(entries -> !entries.isEmpty())
              .orElse(false);
      return new PayoutReadiness(account.getId(), transfers, payouts, outstanding);
    }
  }
}
