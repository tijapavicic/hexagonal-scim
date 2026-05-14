# Data Model and Payment Flow Design for an Art Marketplace

Current as of 2026-05-14.

This document gives a practical domain model for a marketplace selling amateur art with:

- buyer checkout
- seller payouts
- platform commission
- PayPal and bank-based payments

## First design rule

Do not model this as:

- `order.status`
- `payment.status`

only.

You need separate models for:

- order lifecycle
- payment lifecycle
- payout lifecycle
- seller onboarding lifecycle

## Recommended core entities

### Users

Represents any account on the platform.

Key fields:

- `id`
- `email`
- `role`
- `created_at`
- `status`

### SellerProfile

Represents the artist's marketplace identity.

Key fields:

- `id`
- `user_id`
- `display_name`
- `country`
- `is_allowed_to_sell`
- `provider_account_id`
- `provider_onboarding_status`
- `provider_payout_status`

### Listing

Represents an artwork being offered for sale.

Key fields:

- `id`
- `seller_profile_id`
- `title`
- `price_amount`
- `price_currency`
- `type` (`physical`, `digital`)
- `inventory_mode`
- `status`

For one-off artworks, inventory should usually be:

- quantity `1`

### Order

Represents the commercial agreement between buyer and platform for one seller checkout.

Key fields:

- `id`
- `buyer_user_id`
- `seller_profile_id`
- `currency`
- `subtotal_amount`
- `shipping_amount`
- `tax_amount`
- `discount_amount`
- `total_amount`
- `status`
- `payment_id`

### OrderItem

Key fields:

- `id`
- `order_id`
- `listing_id`
- `title_snapshot`
- `unit_price_amount`
- `quantity`

Use snapshots so later listing changes do not mutate old orders.

### Payment

Represents the business-level payment record.

Key fields:

- `id`
- `order_id`
- `provider`
- `provider_payment_id`
- `payment_method_family`
- `payment_method_type`
- `amount`
- `currency`
- `status`
- `buyer_visible_status`
- `failure_reason`
- `paid_at`

### PaymentAttempt

Useful when a buyer retries checkout.

Key fields:

- `id`
- `payment_id`
- `idempotency_key`
- `provider_attempt_id`
- `status`
- `started_at`
- `completed_at`

### Refund

Key fields:

- `id`
- `payment_id`
- `provider_refund_id`
- `amount`
- `reason`
- `status`
- `requested_by`

### Payout

Represents money leaving your platform flow to the seller.

Key fields:

- `id`
- `seller_profile_id`
- `order_id`
- `provider_payout_or_transfer_id`
- `gross_amount`
- `platform_fee_amount`
- `provider_fee_amount`
- `net_amount`
- `currency`
- `status`
- `eligible_at`
- `released_at`
- `paid_at`

### WebhookEvent

Store every provider webhook.

Key fields:

- `id`
- `provider`
- `provider_event_id`
- `type`
- `signature_verified`
- `payload_json`
- `processed_at`
- `processing_status`

### LedgerEntry

This is the minimum serious accounting layer.

Key fields:

- `id`
- `entry_type`
- `order_id`
- `payment_id`
- `payout_id`
- `account_code`
- `direction`
- `amount`
- `currency`
- `created_at`

## Recommended state models

### Seller onboarding states

- `not_started`
- `in_progress`
- `restricted`
- `verified`
- `rejected`

### Order states

- `draft`
- `pending_payment`
- `paid`
- `processing`
- `fulfilled`
- `cancelled`
- `refunded`
- `partially_refunded`
- `disputed`

### Payment states

- `created`
- `checkout_started`
- `submitted`
- `requires_action`
- `processing`
- `paid`
- `failed`
- `cancelled`
- `refunded`
- `partially_refunded`

### Payout states

- `not_eligible`
- `eligible`
- `scheduled`
- `submitted`
- `paid`
- `failed`
- `reversed`

## Recommended payment method model

Do not store payment method as a single generic string only.

Use both:

- `payment_method_family`
- `payment_method_type`

Examples:

- family: `wallet`, type: `paypal`
- family: `real_time_bank`, type: `pay_by_bank`
- family: `bank_transfer`, type: `sepa_bank_transfer`
- family: `bank_redirect`, type: `ideal`

This makes analytics and routing much easier later.

## Recommended flow: seller onboarding

1. Create local `SellerProfile`.
2. Create provider connected account.
3. Redirect seller to provider onboarding.
4. Receive webhook or polling update.
5. Mark seller `verified` only when payout capability is active enough for your business rules.

Important rule:

- do not allow listing publication just because the seller signed up

Require payout and identity readiness first.

## Recommended flow: single-seller checkout

1. Buyer clicks checkout.
2. Backend locks the selected artwork or inventory unit.
3. Backend creates `Order` in `pending_payment`.
4. Backend creates `Payment` in `created`.
5. Backend creates provider checkout session.
6. Buyer completes payment.
7. Provider webhook marks payment `paid` or `failed`.
8. Backend updates `Order`.

Important rule:

- never trust only the browser redirect for payment success

Use the webhook as source of truth.

## Recommended flow: payout release

1. Order becomes `fulfilled`.
2. Background job evaluates payout policy.
3. If eligible, create `Payout` in `scheduled`.
4. Submit payout or transfer via provider.
5. Mark `paid` only after provider confirmation.

## Recommended flow: refund

1. Support or buyer requests refund.
2. Backend validates policy.
3. Create `Refund` record.
4. Call provider refund API.
5. Webhook confirms refund result.
6. Update order, payment, payout eligibility, and ledger.

Important rule:

- if payout already happened, refund and seller recovery become separate workflows

## Recommended flow: dispute or chargeback

1. Provider notifies platform.
2. Store raw dispute event.
3. Freeze new payout release for that seller if your policy says so.
4. Create support case.
5. Update internal risk indicators.

## Commission model

Store commission explicitly per order.

Suggested fields:

- `platform_fee_fixed_amount`
- `platform_fee_percent_bps`
- `platform_fee_total_amount`

Do not infer historical fees from current pricing rules.

## Inventory rule for one-off art

For unique physical or digital pieces:

- reserve inventory before redirecting to checkout
- expire reservation after a short period
- release reservation on failed or abandoned payment

If you skip reservation, you will eventually oversell a unique item.

## Idempotency rules

Use idempotency keys for:

- checkout creation
- refund creation
- payout creation
- webhook processing

Also add unique constraints for:

- provider event IDs
- provider payment IDs
- provider payout IDs

## Reconciliation essentials

At minimum, reconcile:

- internal `Payment` records to provider successful payments
- internal `Payout` records to provider payout reports
- internal commission totals to provider settlement totals

## Suggested schema boundary

Keep provider-specific details in one place:

- `provider_account_id`
- `provider_payment_id`
- `provider_refund_id`
- `provider_payout_or_transfer_id`
- `provider_raw_status`

Keep the rest of the domain model provider-neutral where possible.

## Final modeling advice

If you only do three things right, do these:

1. single-seller checkout in v1
2. webhook-driven payment truth
3. explicit payout and commission records

Those three decisions remove a lot of future pain.
