# MVP Architecture for an Art Marketplace

Current as of 2026-05-14.

This document describes a practical MVP architecture for a marketplace that sells amateur art and supports:

- buyer checkout
- PayPal
- bank-based payments
- seller onboarding
- seller payouts
- platform commissions

## MVP scope

The cleanest MVP is:

- physical or digital art listings
- one seller per checkout
- platform commission on each order
- buyer pays the platform checkout
- seller gets payout later after release rules pass

Do not start with:

- multi-seller basket checkout
- instant payouts
- multiple payout rails per seller
- direct bank integrations

## System boundaries

### Your platform should own

- users
- sellers and onboarding progress
- listings and inventory
- cart and orders
- commission rules
- payout release rules
- customer support workflows
- internal reporting
- your product ledger

### Payment provider should own

- payment method UX
- seller KYC / KYB collection
- payout account collection
- payment authorisation and capture
- payout execution
- external compliance controls in their scope

## Recommended high-level architecture

```text
Web / Mobile App
    |
Marketplace Backend
    |
    +-- Catalog + Listings
    +-- Orders
    +-- Payment Orchestrator
    +-- Seller Onboarding
    +-- Payout Release Service
    +-- Ledger / Reporting
    +-- Admin / Ops API
    |
    +-- Payment Provider API
    +-- Webhook Ingestion
    +-- Email / Notifications
```

## Core components

### 1. Frontend

Use the frontend for:

- browsing art
- cart and checkout
- seller onboarding status
- seller payout visibility

Do not let the frontend decide payment truth.
It should only reflect backend-confirmed states.

### 2. Marketplace backend

This is your system of orchestration.

It should:

- create checkout sessions
- associate payments with orders
- receive webhook events
- decide when a seller payout can be released
- maintain your internal order and payout state

### 3. Payment provider integration

Keep provider logic behind a single internal module, for example:

- `createSellerAccount()`
- `createCheckout()`
- `handleWebhook()`
- `createRefund()`
- `createTransferOrPayout()`
- `getConnectedAccountStatus()`

That makes later provider changes survivable.

### 4. Webhook processor

Webhooks are first-class infrastructure.

Use them for:

- payment succeeded
- payment failed
- refund created
- refund succeeded
- payout paid
- payout failed
- connected account updated
- dispute created

Requirements:

- verify signatures
- store raw payloads
- idempotent processing
- retry-safe handlers

### 5. Background workers

Use jobs for:

- payout release evaluation
- retries on provider polling
- reconciliation imports
- reminder emails
- stale-order cleanup

## Recommended payment flow

### Seller onboarding

1. Seller signs up.
2. Your backend creates a connected account with the provider.
3. Seller completes hosted onboarding.
4. Provider verifies identity and payout details.
5. Your platform stores onboarding status and blocks selling until requirements are satisfied.

### Buyer checkout

1. Buyer places items in cart.
2. Backend validates inventory and ownership.
3. Backend creates an order in `pending_payment`.
4. Backend creates a checkout session or payment intent with the provider.
5. Buyer completes payment with `PayPal`, `Pay by Bank`, card, or local bank method.
6. Your backend waits for webhook confirmation.
7. On success, order moves to `paid`.

### Payout release

1. Order becomes `eligible_for_payout` only after your release policy passes.
2. Backend creates transfer / payout action.
3. Provider processes payout to seller bank account.
4. Webhook marks payout `paid` or `failed`.

## Release policy recommendation

For an art marketplace, do not pay sellers immediately in v1.

Use a release policy such as:

- item accepted by seller
- shipping started or digital delivery completed
- short cooling-off period passes
- no open support case
- no fraud flag

This gives you room for:

- cancellations
- item availability issues
- first-day fraud
- obvious support disputes

## Data stores

Use one primary relational database first.

Recommended major tables:

- `users`
- `seller_profiles`
- `seller_onboarding`
- `listings`
- `inventory_units`
- `orders`
- `order_items`
- `payments`
- `payment_attempts`
- `refunds`
- `payouts`
- `ledger_entries`
- `webhook_events`

Do not split this into many microservices early unless your team is already optimized for that style.

## Ledger recommendation

Even in an MVP, keep a small internal ledger.

Track:

- buyer charge amount
- platform commission
- provider fee estimate or actual fee
- seller payable amount
- refund impacts
- payout impacts

You do not need a huge finance platform in week one, but you do need a durable money record.

## Security and compliance basics

At minimum:

- do not store raw payout account details unless required
- rely on provider-hosted onboarding where possible
- verify webhook signatures
- restrict admin actions
- log all money-moving operations
- use idempotency keys on provider writes

## Operations tooling

Build a small admin screen early with:

- seller onboarding status
- order payment state
- payout state
- refund action
- raw provider references
- webhook delivery history

This saves enormous engineering time later.

## What to postpone

Push these out of v1 if you can:

- multi-seller basket checkout
- seller-chosen payout schedules
- wallet balances
- instant payouts
- direct PayPal payouts to artists
- advanced treasury flows

## Suggested MVP stack

- frontend: whatever your team already uses
- backend: your current service stack
- database: PostgreSQL
- payment provider: `Stripe Connect` or `Mollie Connect`
- webhook queue: database-backed jobs or a message queue
- object storage: art assets and seller documents that are yours to store

## Final architecture advice

The best MVP architecture here is boring on purpose:

- one backend
- one relational database
- one marketplace payment provider
- webhook-driven state updates
- manual payout release policy

That is usually the fastest path to a stable launch.
