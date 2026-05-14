# Bank Payments for Software Engineers

Practical engineering notes for building software that moves money between bank accounts.

Current as of 2026-05-14.

This guide focuses on bank-account payments and payment infrastructure:

- ACH, wire, instant payments, SEPA, Faster Payments, SWIFT, correspondent banking
- Bank payment products such as payout flows, account-to-account transfers, bill pay, collections, treasury flows, and embedded finance

It does not go deep on card acquiring, card network disputes, or merchant acquiring unless they intersect with bank transfers.

## 1. The Mental Model

If you only remember one thing, remember this:

**A bank payment is not "an API call that moved money". It is a multi-party, stateful, regulated workflow with asynchronous outcomes, partial failures, and strict audit requirements.**

In production, the hard parts are usually:

- modeling money correctly
- modeling state transitions correctly
- handling retries without duplicating movement
- reconciling your ledger to bank reality
- dealing with reversals, rejects, returns, recalls, and investigations
- keeping fraud and compliance controls strong without breaking UX

## 2. The Core Concepts

### 2.1 Payment instruction vs payment execution vs settlement

People often mix these up.

- **Instruction**: "Please send EUR 500 from A to B"
- **Execution**: the payment system accepts and processes the instruction
- **Clearing**: participants exchange and validate obligations
- **Settlement**: funds move between financial institutions
- **Posting**: a bank updates the customer-facing account balance and transaction history

Your software must treat these as different events.

### 2.2 Push vs pull

- **Push payment**: the payer tells their bank to send money
  - examples: wire, FedNow, RTP, SEPA Credit Transfer, Faster Payments
- **Pull payment**: the payee initiates a collection against the payer's account, usually with a mandate or authorization
  - examples: ACH debit, SEPA Direct Debit

Why this matters:

- push flows are more exposed to authorized push payment fraud and bad destination details
- pull flows are more exposed to returns, revocations, mandate issues, and unauthorized-debit claims

### 2.3 Finality

Not all "successful" payments are equally final.

- **RTGS / wire-style finality**: once settled, the interbank leg is typically final and irrevocable
- **Instant-payment finality**: often immediate and intended to be final, but operational return and exception processes still exist
- **Batch/debit rails**: may allow returns or reversals for defined windows and reasons

Engineering consequence:

- never collapse all terminal states into a single `paid`
- model `settled`, `returned`, `recalled`, `reversed`, `rejected`, `expired`, `cancelled` separately

### 2.4 Batch vs real-time vs near-real-time

- **Batch**: files collected and processed in windows; outcomes may appear later
- **Real-time / RTGS**: each payment settles individually
- **Instant payments**: always-on or near-always-on systems that process within seconds

Batch systems usually optimize cost and scale.
Real-time systems optimize urgency and certainty.

### 2.5 Clearing account reality

There are at least four balances you may care about:

- customer ledger balance in your system
- bank account balance
- available balance
- settlement / prefunding / liquidity balance at the scheme or sponsor bank

A lot of payment bugs are really balance-model bugs.

## 3. The Parties Involved

In a real bank-payment flow, you may have:

- customer or business user
- your platform
- your ledger
- your bank or sponsor bank
- an originator bank
- a beneficiary bank
- a payment scheme or market infrastructure
- a messaging network
- AML / sanctions / fraud providers
- KYC / KYB vendors
- open-banking or bank-linking providers

Do not design the system as if there are only "sender" and "receiver".

## 4. The Payment Rails You Need to Know

### 4.1 United States

#### ACH

ACH is the default U.S. bank-account rail for low-cost transfers, payroll, bill pay, and recurring collections.

Key characteristics:

- batch-oriented
- supports both credits and debits
- cheaper than wires and instant payments
- asynchronous outcomes
- strong file-format and return-code culture
- widely used for payroll, direct deposit, autopay, and account-to-account transfers

Engineering implications:

- file ingestion and generation matter
- cut-off times matter
- same-day vs next-day behavior matters
- return handling is a first-class feature, not an edge case
- authorization evidence for debits matters

Things engineers should know:

- ACH credits and ACH debits behave differently
- same-day ACH changes both speed expectations and operational deadlines
- returns, NOCs, and prenotes affect operational design
- many integrations still revolve around fixed-width ACH files, SFTP, and operator windows

#### Wire transfers

U.S. wires are used for high-value, time-critical movement.

Key characteristics:

- individually processed
- same-day and time-critical
- higher cost
- strong finality expectations
- often used for treasury, settlements, real estate, and urgent business payments

Engineering implications:

- richer control workflows are needed before release
- approvals, dual control, limit checks, and manual review are common
- investigations and returns are exception processes, not normal reversibility

#### RTP (The Clearing House)

RTP is a U.S. instant-payment rail.

Key characteristics:

- 24/7/365
- credit push
- immediate clearing and settlement
- immediate finality expectations
- rich ISO 20022 messaging
- supports request for payment

Engineering implications:

- funds availability is immediate, so fraud controls must be front-loaded
- request-for-payment flows need different UX and state models from direct pushes
- reconciliation is easier than ACH in some ways, but failed controls can be more expensive because money moves instantly

#### FedNow

FedNow is the Federal Reserve's U.S. instant-payment infrastructure.

Key characteristics:

- 24/7/365
- real-time payments within seconds
- immediate recipient access to funds
- ISO 20022 messaging
- supports request for payment and liquidity management transfer functionality

Engineering implications:

- the system design is closer to RTP than to ACH
- fraud, sanctions, and entitlement checks must happen before release
- operational support must be ready outside business hours

### 4.2 Europe / SEPA

#### SEPA Credit Transfer (SCT)

Standard euro bank transfer across SEPA.

Key characteristics:

- credit push
- not necessarily instant
- commonly used for regular account transfers and payouts
- structured identifiers such as IBAN and increasingly richer ISO 20022 data

Engineering implications:

- cut-off times and bank processing windows still matter
- beneficiary data quality matters
- remittance information quality matters for reconciliation

#### SEPA Instant Credit Transfer (SCT Inst)

Instant euro bank transfer across participating PSPs.

Key characteristics:

- funds available in less than ten seconds
- 24/7/365
- strong push toward pan-European reachability
- increasingly tied to verification-of-payee expectations and modern ISO 20022 practices

Engineering implications:

- treat it like an instant-payment product, not just a faster batch product
- fraud controls must happen before sending
- beneficiary verification becomes part of the UX and risk model

#### SEPA Direct Debit (SDD)

Pull-based collection scheme using payer mandates.

Key characteristics:

- mandate-based
- common for subscriptions, invoices, utilities, and recurring collections
- return, refund, and revocation mechanics matter

Engineering implications:

- mandate storage and auditability are critical
- collection scheduling and retry policies matter
- dispute and refund windows must be part of your product design

#### TIPS

TIPS is Eurosystem infrastructure for instant-payment settlement in central bank money.

What matters for engineers:

- SCT Inst is the scheme customers see
- TIPS is part of the settlement infrastructure underneath
- if you are building against a PSP or sponsor bank, you usually integrate with their APIs or files, not directly with TIPS

Still, understanding TIPS helps you reason about:

- 24/7 settlement expectations
- liquidity and reachability
- instant-payment operational requirements

### 4.3 United Kingdom

#### Faster Payments

The UK's main account-to-account faster retail payment system.

Key characteristics:

- near real-time user experience
- broad retail and business use
- credit push
- distinct from Bacs and CHAPS

Engineering implications:

- expect users to treat it as "instant" even if operational exceptions exist
- confirmation-of-payee style name checking matters
- APP fraud controls are central

#### CHAPS

The UK's high-value same-day payment rail.

Key characteristics:

- high-value, time-critical payments
- RTGS settlement
- irrevocable once settled between participants

Engineering implications:

- similar operating mindset to wires
- dual approval, controls, time windows, and investigations matter

### 4.4 Cross-border payments

Cross-border bank payments are usually the most misunderstood.

#### SWIFT is not the money

SWIFT is primarily a messaging network and standards ecosystem. In many cases it is not the settlement layer itself.

Cross-border payments often involve:

- SWIFT messages
- correspondent banking relationships
- nostro / vostro accounts
- FX conversion
- local clearing in the destination country
- sanctions and AML screening across multiple hops

Engineering implication:

- "message accepted by SWIFT" does **not** automatically mean "beneficiary has funds"

#### Correspondent banking

When banks do not hold direct accounts with each other in every currency and jurisdiction, they use correspondent relationships.

What this means in software:

- the payment may traverse intermediaries
- fees may be deducted along the path
- timestamps across hops matter
- investigations are normal
- status transparency is harder than in domestic instant-payment rails

#### FX and value dating

Cross-border systems introduce:

- rate sourcing
- quote locking
- spread and fee disclosure
- cut-offs by currency
- business-day calendars by country
- holidays per currency corridor
- settlement delays due to local compliance or local clearing windows

If you support cross-border payments, your design must include:

- quoted rate and expiry time
- booked rate
- instructed amount
- debit amount
- fees
- expected beneficiary amount
- actual beneficiary amount if available

## 5. Payment Message and Data Design

### 5.1 A payment is not just amount + account number

At minimum, a robust payment record usually needs:

- internal payment ID
- idempotency key
- customer ID / account ID
- source account
- destination account
- source and destination bank identifiers
- scheme / rail
- amount
- currency
- fees
- exchange rate if any
- remittance / reference text
- purpose code if applicable
- requested execution date
- submission timestamp
- acceptance timestamp
- settlement timestamp
- reconciliation IDs from counterparties
- scheme status code
- normalized internal status
- failure / return / reject reason

For multi-rail products, add:

- payment priority
- rail-selection reason
- retry / reroute policy
- risk decision ID
- sanction-screening decision ID
- authorization artifact ID

### 5.2 External identifiers matter

Depending on geography, you may encounter:

- IBAN
- BIC / SWIFT code
- ABA routing number
- account number
- sort code
- national clearing code
- UETR
- scheme reference
- IMAD / OMAD for wires

Do not throw these into one generic `bank_code` string and hope for the best.

### 5.3 ISO 20022 matters

ISO 20022 is increasingly the common language across modern payment systems.

Why engineers should care:

- richer structured data
- better reconciliation
- better compliance data
- less manual repair work
- improved straight-through processing

Design guidance:

- keep structured address, party, and remittance fields available in your domain model
- avoid flattening everything into one free-text string
- preserve raw scheme messages for audit and debugging
- keep normalized internal representations alongside raw originals

## 6. State Machines You Actually Need

Most payment systems are under-modeled.

Do not use:

- `PENDING`
- `SUCCESS`
- `FAILED`

Use a richer lifecycle.

Example high-level outbound payment state machine:

1. `draft`
2. `awaiting_customer_auth`
3. `awaiting_risk_review`
4. `authorized`
5. `queued_for_submission`
6. `submitted_to_bank`
7. `accepted_by_scheme`
8. `settled`
9. `rejected`
10. `returned`
11. `recalled`
12. `reversed`
13. `expired`
14. `cancelled`

You may also need parallel dimensions:

- operational status
- customer-visible status
- accounting status
- compliance status

Example:

- customer sees `completed`
- operations sees `settled_pending_reconciliation`
- accounting sees `credit_posted_unreconciled`

That is normal.

## 7. Ledgering and Accounting

If you build payment software without a proper ledger, it will eventually hurt you.

### 7.1 Use double-entry accounting

At minimum, model:

- debits
- credits
- immutable journal entries
- balances derived from journal entries
- posting timestamps
- references to the business event that caused the entry

Avoid mutable "current balance" as your system of record.

### 7.2 Separate business intent from accounting effect

One payment may produce many ledger entries:

- reserve funds
- collect fee
- release reserve
- settle outbound principal
- book FX spread
- post return
- write off investigation fee

Keep the business event model and ledger-entry model separate but linked.

### 7.3 Distinguish balance types

You may need:

- current balance
- available balance
- pending outgoing
- pending incoming
- reserved balance
- settled balance
- reconciled balance

Users care about "Can I spend this now?"
Finance cares about "What is legally and operationally final?"

Those are different questions.

## 8. Idempotency, Concurrency, and Exactly-Once Myths

Payments punish sloppy distributed-systems thinking.

### 8.1 Idempotency is mandatory

You need idempotency for:

- API requests
- webhook processing
- file ingestion
- job retries
- bank callback handling
- ledger posting

Every outward money movement must be protected against duplicate submission.

### 8.2 Exactly once is usually a story you tell yourself

In practice, most payment systems are built from:

- at-least-once delivery
- deduplication keys
- immutable events
- reconciliation jobs
- operator tooling

The real goal is:

**no unintended duplicate economic effect**

### 8.3 Concurrency controls matter

Protect against:

- two workers submitting the same payment
- duplicate customer clicks
- retries after timeout where the bank actually succeeded
- race between cancellation and submission
- race between return and user-visible completion

Useful patterns:

- unique constraints
- compare-and-swap state transitions
- append-only event logs
- outbox pattern
- bank submission locks

## 9. Reconciliation

Reconciliation is not a back-office nice-to-have. It is part of the product.

You need to reconcile:

- your internal ledger
- your processor or sponsor bank statements
- scheme reports
- bank acknowledgements
- bank intraday files
- end-of-day statements
- fee reports

### 9.1 Types of reconciliation

- **transaction reconciliation**: did this specific payment happen?
- **cash reconciliation**: do balances and statements add up?
- **fee reconciliation**: did we charge and receive the right fees?
- **exception reconciliation**: are returns, recalls, and rejects fully reflected?

### 9.2 Build exception queues

Your system should make it easy to answer:

- which payments are unmatched?
- which were accepted but not settled?
- which settled at the bank but not in our ledger?
- which were returned but not customer-notified?

## 10. Returns, Rejects, Reversals, Recalls, and Investigations

These words are not interchangeable.

- **Reject**: instruction failed before settlement or before full processing
- **Return**: funds sent back after a prior acceptance or settlement event, depending on scheme rules
- **Reversal**: undoing a prior accounting effect or scheme action under defined rules
- **Recall**: sender asks for the money back; success is not guaranteed
- **Investigation**: operational inquiry into payment status or issue

Design these as separate workflows with distinct permissions, deadlines, and messaging.

## 11. Fraud, Security, and Compliance

This is where many engineering teams under-scope.

### 11.1 KYC / KYB / CIP

If you onboard customers or businesses into a money-movement product, expect identity obligations.

Depending on jurisdiction and business model, this can include:

- customer identification
- beneficial ownership
- sanctions exposure
- risk scoring
- transaction monitoring

Do not assume "our bank partner handles all of it" unless contracts and control boundaries are explicit.

### 11.2 Sanctions screening

You may need to screen:

- sender
- receiver
- counterparties
- banks
- payment message text
- countries and corridors

Important engineering point:

- screening is not just a one-time onboarding check
- payment-time screening and rescreening can matter

### 11.3 AML transaction monitoring

Expect to support signals such as:

- unusual velocity
- round-dollar or structured patterns
- account takeovers
- first-time beneficiary risk
- corridor anomalies
- rapid in-and-out movement
- mule indicators

Design for:

- rule versioning
- feature logging
- explainable alerts
- case management hooks

### 11.4 Authentication and authorization

Distinguish:

- who is logged in
- who is allowed to create a payment
- who is allowed to approve a payment
- whether step-up authentication is required

Common controls:

- MFA / SCA
- dual approval
- spend limits
- per-beneficiary controls
- session risk evaluation
- transaction signing or dynamic linking where required

### 11.5 Account and payee verification

Before moving money, many systems now validate destination details.

Examples:

- microdeposits
- prenotes
- open-banking account verification
- Confirmation of Payee
- Verification of Payee

Engineers should design for:

- exact match
- close match
- no match
- verification unavailable

Those outcomes change both UX and fraud posture.

### 11.6 Secrets and data protection

Treat payment data as highly sensitive.

At minimum:

- encrypt in transit
- encrypt at rest
- minimize storage of bank credentials
- tokenize where possible
- redact sensitive data in logs
- lock down operator access
- maintain strong audit trails

## 12. UX Rules That Matter More Than Teams Expect

Payment UX is not just polish. It changes fraud rates, support load, and legal exposure.

### 12.1 Always show the rail and timing expectation

Users need to know:

- instant vs same-day vs next-business-day
- cut-off implications
- weekend / holiday effects
- whether the payment is likely reversible

### 12.2 Display beneficiary details clearly

Show:

- bank name where available
- masked account identifiers
- payee name
- verification result
- fees
- FX details
- expected arrival timing

### 12.3 Explain pending states honestly

Do not claim money is sent if you only created a request internally.

Better customer-facing progression:

1. `scheduled`
2. `processing`
3. `sent to bank`
4. `completed`
5. `returned`

### 12.4 Surface reference data

Support teams and users need references such as:

- payment ID
- bank reference
- UETR
- wire reference
- return reason

## 13. Operational Readiness

Money movement is an operations product.

You need:

- dashboards by rail
- alerting by failure mode
- aging queues
- manual review tools
- replay tooling
- reconciliation tooling
- operator notes
- case / investigation workflows
- safe admin controls with full audit logs

### 13.1 Design for out-of-hours support

If you offer instant payments, your outages are not "business hours only".

You need plans for:

- 24/7 alerting
- partial provider outages
- bank holiday staffing
- safe degradation paths

### 13.2 Know your cut-offs and calendars

Per rail and per country, store:

- business-day calendar
- cut-off times
- settlement windows
- maintenance windows
- daylight savings behavior

Never bury these in code constants.

## 14. Testing Strategy

Payment systems need unusually serious test coverage.

### 14.1 Unit and property tests

Focus on:

- money math
- status transitions
- idempotency rules
- fee calculations
- FX quote expiry
- return code mapping

### 14.2 Integration tests

Cover:

- bank API happy paths
- file generation and parsing
- duplicate callbacks
- timeouts followed by late success
- reject then retry
- settlement then return
- reconciliation import

### 14.3 Failure injection

Simulate:

- network timeout after provider processed the request
- provider 500 after acceptance
- duplicate webhook delivery
- out-of-order events
- corrupted reconciliation file
- bank holiday scheduling edge cases

### 14.4 Test data design

Keep canonical fixtures for:

- domestic payment
- cross-border payment
- ACH debit return
- wire investigation
- instant-payment no-match payee verification
- sanctions hit
- partial reconciliation mismatch

## 15. Architecture Patterns That Work Well

No single pattern is mandatory, but these tend to help.

### 15.1 Separate orchestration from ledgering

One service can own payment workflow.
Another can own immutable accounting entries.

Why:

- cleaner reasoning
- easier auditability
- less chance of state and balance drifting together silently

### 15.2 Keep raw external payloads

Store:

- raw request sent
- raw response received
- raw callback/webhook
- raw file line or message

And also store:

- parsed normalized fields
- derived internal state

This is invaluable for support, audits, and disputes.

### 15.3 Use a rail adapter boundary

For multi-rail systems, create a port or adapter model per rail:

- `submitPayment`
- `cancelPayment`
- `getPaymentStatus`
- `parseCallback`
- `parseStatementLine`
- `mapReturnReason`

Do not leak rail-specific oddities all over the domain layer.

### 15.4 Prefer append-only eventing for core state changes

You still may maintain materialized current state, but keep durable event history for:

- authorization
- submission
- acceptance
- settlement
- return
- reconciliation

## 16. Common Product Patterns

### Payouts

Used for creator payouts, payroll-like disbursements, insurance claims, marketplace seller funds, and refunds.

Design priorities:

- beneficiary verification
- cut-off aware scheduling
- bulk payment support
- returns handling
- treasury and liquidity controls

### Pull collections

Used for subscriptions, invoice collection, loan repayment, and recurring debit.

Design priorities:

- mandate lifecycle
- retry scheduling
- return / unauthorized-debit handling
- customer notification rules

### Internal transfer / wallet-to-bank / bank-to-wallet

Design priorities:

- reservation and release model
- instant vs delayed payout choice
- ledger consistency
- balance presentation

### Treasury and enterprise payments

Design priorities:

- role-based approvals
- batch initiation
- ERP references
- reconciliation exports
- cut-offs and exception handling

## 17. Build vs Buy

Usually you are not building a bank from scratch. You are composing:

- ledger
- bank connection
- payments processor
- fraud stack
- KYC / KYB
- sanctions / AML
- reconciliation pipeline
- support tooling

Questions to ask before building deeply in-house:

- Do we need direct scheme access or will a sponsor / processor do?
- Do we need file rails, API rails, or both?
- Do we need multi-country support soon?
- Do we need real-time fraud decisions before release?
- Who owns regulatory controls contractually?
- Can our operations team handle investigations and returns at scale?

The engineering cost is rarely just the send-payment API.
It is the years of operational surface area around it.

## 18. Mistakes That Break Payment Systems

Common mistakes:

- treating bank payments like synchronous CRUD
- using only three statuses
- not having double-entry ledgering
- relying on mutable balances as source of truth
- not protecting against duplicate submission
- not storing raw provider payloads
- not modeling cut-offs and holidays
- not building reconciliation from day one
- assuming "accepted" means "settled"
- assuming instant rails can be reversed like ACH
- skipping operator tooling
- treating fraud and sanctions as an afterthought

## 19. Practical Checklist for a First Serious Implementation

If I were designing a new bank-payments platform, I would want this minimum checklist:

- clear payment domain model per rail
- explicit state machine
- immutable double-entry ledger
- idempotent outward submission
- raw payload retention
- reconciliation jobs and exception queues
- manual review tooling
- customer-visible references and honest statuses
- cut-off and holiday engine
- fraud and sanctions decision hooks before release
- support for returns / rejects / recalls
- strong audit trail for every operator and system action

## 20. What to Learn Next

If this area becomes core to your product, the next topics to go deep on are:

1. ACH file formats, returns, and authorization evidence
2. ISO 20022 message families such as `pacs`, `pain`, and `camt`
3. sponsor-bank and processor integration patterns
4. ledger design and reconciliation architecture
5. sanctions screening and transaction monitoring operating model
6. instant-payment fraud controls
7. cross-border correspondent banking and FX operations

## 21. Reference Architecture Thought

For most teams, a healthy bank-payments architecture looks like:

- product API layer
- payment orchestration service
- risk / compliance decisioning
- ledger service
- rail adapters
- reconciliation pipeline
- operations console
- reporting / finance exports

This is one of those domains where "boring and explicit" beats "clever and abstract".

## 22. Official References

These are good primary or official references for the rails and topics mentioned above:

- Federal Reserve, FedNow Service: <https://www.frbservices.org/financial-services/fednow/about.html>
- Federal Reserve, FedNow resources and operating materials: <https://www.frbservices.org/resources/financial-services/fednow>
- Federal Reserve, Fedwire Funds Service: <https://www.frbservices.org/financial-services/wires>
- Federal Reserve Board, Payment System Risk policy: <https://www.federalreserve.gov/paymentsystems/psr_about.htm>
- Nacha ACH Developer Guide, How ACH Works: <https://achdevguide.nacha.org/index.php/how-ach-works>
- Nacha, What is ACH: <https://www.nacha.org/what-ach>
- The Clearing House, RTP network overview: <https://www.theclearinghouse.org/payment-systems/rtp>
- The Clearing House, RTP FAQ / institution information: <https://www.theclearinghouse.org/payment-systems/rtp/institution>
- European Central Bank, TIPS: <https://www.ecb.europa.eu/paym/target/tips/html/index.pl.html>
- European Payments Council, SCT Inst rulebook page: <https://www.europeanpaymentscouncil.eu/what-we-do/epc-payment-schemes/sepa-instant-credit-transfer/sepa-instant-credit-transfer-rulebook>
- European Payments Council, Verification Of Payee: <https://www.europeanpaymentscouncil.eu/what-we-do/other-schemes/verification-payee>
- European Banking Authority, PSD2 SCA and secure communication RTS: <https://www.eba.europa.eu/regulation-and-policy/payment-services-and-electronic-money/regulatory-technical-standards-on-strong-customer-authentication-and-secure-communication-under-psd2>
- Pay.UK, Faster Payment System: <https://www.wearepay.uk/what-we-do/payment-systems/faster-payment-system/>
- Pay.UK, Overlay services including Confirmation of Payee: <https://www.wearepay.uk/what-we-do/overlay-services/>
- Bank of England, RTGS and CHAPS overview: <https://www.bankofengland.co.uk/payment-and-settlement/a-brief-introduction-to-the-real-time-gross-settlement-system-and-chaps>
- Swift, ISO 20022: <https://www.swift.com/standards/iso-20022>
- Swift, payments and cross-border payment tracking: <https://www.swift.com/payments>
- Swift, correspondent banking: <https://www.swift.com/payments/correspondent-banking>
- OFAC sanctions list search: <https://ofac.treasury.gov/sanctions-list-search-tool>
- FinCEN suspicious activity reporting: <https://www.fincen.gov/money-services-business-msb-suspicious-activity-reporting>

## 23. Final Advice

If you are building bank-payment software, think like all three at once:

- distributed-systems engineer
- accountant
- risk operator

If you only think like one of them, the system will eventually fail in production.
