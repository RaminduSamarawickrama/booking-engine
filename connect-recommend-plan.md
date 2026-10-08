## Recommended Connect integration

Airport transfer marketplace: customers book and pay the platform; independent drivers fulfil each ride leg and are paid after completion. Covers Stripe Payments, Connect and Invoicing.

### A. Account configuration
- Accounts API: `/v2/core/accounts`
- Legacy account `type`: not used
- Dashboard: Express (lightweight view for drivers)
- Fee collection: your platform manages pricing (`fees_collector: "application"`)
- Negative balance liability: your platform (`losses_collector: "application"`)

Drivers are individuals who need onboarding, bank details and a payout view, which the Express dashboard covers. The platform sets prices, takes payment and handles support, so it owns pricing and negative balance liability.

Each driver account needs the recipient configuration (`configuration.recipient`) with `stripe_transfers` on `stripe_balance` requested, so the account can receive transfers from the platform. Driver accounts do NOT request the merchant configuration or the `card_payments` capability: drivers never take payments directly, and requesting them lengthens onboarding.

### B. Charge pattern: separate charges and transfers
Customers pay the platform at booking, often before a driver is assigned. A return trip's two legs can go to different drivers on different days, and each driver is paid only after their leg is completed. Separate charges and transfers fits all three: one charge per booking, one transfer per completed leg.

### C. Driver onboarding flow
Onboarding method: Stripe-hosted, opened from the driver app with an Account Link.
Embedded components run in a browser and the driver app is native React Native, so a hosted link is the lowest-effort path. The admin web app can embed onboarding later.

1. Admin approves the driver; fleet-service creates the v2 Account (recipient configuration, Express dashboard, platform responsibilities).
2. The driver opens the onboarding link; Stripe collects identity and bank details.
3. fleet-service syncs capability status from account webhooks and on demand.
4. The driver can receive offers before verification completes, but transfers stay blocked until `stripe_transfers` is active.
5. The driver app shows a "Stripe needs more information" banner whenever requirements are outstanding.

### D. Payments dashboard access for drivers
The driver app's Earnings screen opens the Express dashboard through a fresh login link created by the backend each time.

### E. Embedded components (admin web or later web views)
- `account_onboarding`
- `notification_banner` (required; keeps drivers enabled as requirements change)
- `account_management`
- `payouts`

Payments views show reduced detail with separate charges and transfers.

### F. Webhook integration
Use webhooks for payment confirmation, transfers and disputes, always verifying the Stripe signature before processing ([webhook signature verification](https://stripe.com/docs/webhooks/signatures)).

### G. Onboarding status gating
Before creating a transfer, retrieve the account (`v2/core/accounts/{id}` with the recipient configuration included) and require:
- `configuration.recipient.capabilities.stripe_balance.stripe_transfers.status === 'active'`
- `configuration.recipient.capabilities.stripe_balance.payouts.status` shown to the driver

Do not rely on v1 `charges_enabled` or `payouts_enabled`. Do not explicitly request `stripe_balance.payouts`; it is requested automatically with `stripe_transfers`.

### H. Fee structure
- Platform fee model: 25% commission per ride leg (configurable)
- `application_fee_amount`: not used — it is not compatible with separate charges and transfers. The commission is kept through transfer math: driver transfer = 75% of the leg's commissionable amount.
- Platform margin per booking = charge amount − driver transfers − Stripe processing fees (− VAT owed, if registered)
- The platform pays Stripe's processing fees out of its 25%. Check [stripe.com/pricing](https://stripe.com/pricing) for regional rates and monitor the [margin report](https://docs.stripe.com/connect/margin-reports.md).

```
 Customer pays £100 for a leg
         │
         ▼
  ┌──────────────────┐
  │  Your platform   │ ─── keeps £25 minus Stripe processing fees
  └────────┬─────────┘
           │ transfer £75 after the ride is COMPLETED
           │ (source_transaction = booking's charge)
           ▼
  ┌──────────────────┐
  │      Driver      │ ─── receives £75, paid out on the Express schedule
  └──────────────────┘
```

### I. SaaS monetization
Not applicable: drivers are not billed a subscription.

### J. Implementation plan
1. Account setup — complete the Connect platform profile in the Dashboard (negative balance liability acknowledgement); fleet-service creates v2 recipient accounts and stores `stripe_account_id` per driver.
2. Onboarding — Account Link endpoint for the driver app; capability sync from account webhooks; transfers gated on `stripe_transfers` being active.
3. Payments — payment-service creates one PaymentIntent per booking with `transfer_group = <booking reference>`; Payment Element on web, PaymentSheet in React Native; booking confirmation only from a signature-verified webhook.
4. Transfers — on `RideCompleted`, create a transfer of 75% of the leg's commissionable amount with `source_transaction = <booking charge>`, idempotency key `transfer:<rideId>`, and booking/leg metadata. If the driver's account is not ready, queue and retry when it becomes active.
5. Refunds and disputes — refund before the ride: plain refund. Refund after the ride: refund plus reversal of that leg's transfer. Dispute: explicit reversal of the related transfers (never automatic). Alert ops when a reversal fails.
6. Invoicing (corporate accounts) — monthly invoice on the platform account (`collection_method: "send_invoice"`, `days_until_due`), one line item per completed leg, no `transfer_data`; drivers still paid per completed ride by transfer.
7. Go-live checks — test cards, onboarding test data, transfer and reversal tests, a test dispute, margin report review.

### K. Risk and liability
#### Negative balance liability
Owner: your platform. When a customer disputes a charge, Stripe debits the platform balance. The platform recovers the driver's share by reversing the transfer, which can take the driver's balance negative; that recovery path requires platform-owned negative balance liability.

#### Risk management
Owner: your platform, with Radar default rules. Fraudulent charges that get through come out of the platform balance.

> Caution: with this charge pattern drivers cannot handle refunds or disputes from the Express dashboard. The platform must run webhook-driven refund, dispute and transfer-reversal handling.

### L. Why this fits your business
- Customers book and pay the platform, which appears on their statement: the platform is merchant of record.
- Drivers are assigned after payment and a return booking can span two drivers: separate charges and transfers.
- Paying drivers only after completion protects the platform against no-shows and cancellations.
- Express dashboard and hosted onboarding keep the driver app light.

### M. Open questions
- Stripe account country (must be a Stripe-supported country such as the UK; drivers must be in a region the platform account supports).
- Commission base: before or after VAT, return discounts and paid extras.
- Corporate invoices: pay drivers at ride completion (platform-funded) or after the invoice is paid.
- Driver share of retained no-show or late-cancellation fees.
